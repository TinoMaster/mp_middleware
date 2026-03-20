package it.ariaspa.mypay.mypaycore.api.soap.endpoint;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient;
import it.ariaspa.mypay.mypaycore.api.logging.TransactionLoggingService;
import it.ariaspa.mypay.mypaycore.api.metrics.MiddlewareMetricsService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Endpoint SOAP per la riconciliazione dei pagamenti telematici (mypivot).
 *
 * <p>Questo endpoint riceve le richieste SOAP dai sistemi SIL (Sistemi Informativi Locali)
 * sul path {@code /ws/pivot/PagamentiTelematiciPagatiRiconciliati} e le instrada
 * verso il backend corretto in base alla configurazione dell'ente nel database.
 *
 * <p>Flusso (Fase 9 — routing dinamico PU/legacy con logging e metriche):
 * <ol>
 *   <li>Il SIL invia una richiesta SOAP a questo endpoint</li>
 *   <li>L'endpoint estrae {@code codIpaEnte} dall'Header SOAP e il {@code tipoOperazione}
 *       dal local part del messaggio</li>
 *   <li>Il {@link RoutingDecisionService} decide la destinazione e la modalita:
 *       <ul>
 *         <li><strong>PIATTAFORMA_UNITARIA</strong>: inoltro con OAuth2 via
 *             {@link PiattaformaUnitariaClient}</li>
 *         <li><strong>LEGACY</strong>: forward diretto via {@link ProxyForwardingClient}</li>
 *       </ul>
 *   </li>
 *   <li>La risposta viene restituita al SIL</li>
 *   <li>La transazione viene registrata nel log DB ({@link TransactionLoggingService})
 *       e nelle metriche Micrometer ({@link MiddlewareMetricsService})</li>
 * </ol>
 *
 * <p>Il path esposto ({@code /ws/pivot/...}) replica il path originale del backend mypivot,
 * in modo che i SIL non debbano modificare nulla.
 *
 * <p>Namespace:
 * <ul>
 *   <li>Header: {@code http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead}</li>
 *   <li>Body: {@code http://www.regione.veneto.it/pagamenti/pivot/ente/}</li>
 * </ul>
 *
 * <p>Sicurezza XML:
 * <ul>
 *   <li>Prevenzione XXE (XML External Entity) attacks</li>
 *   <li>Disabilitazione DTD e entity esterne nel parser XML</li>
 *   <li>TransformerFactory sicura senza accesso a DTD/stylesheet esterni</li>
 * </ul>
 */
@Endpoint
public class ReconciliationEndpoint {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEndpoint.class);

    /**
     * Namespace URI per le operazioni di riconciliazione (body della PU).
     * Corrisponde al namespace reale usato dalla Piattaforma Unitaria pagoPA.
     */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/pivot/ente/";

    /**
     * Namespace URI per l'header SOAP della PU (intestazionePPT con codIpaEnte).
     */
    static final String HEADER_NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead";

    /**
     * Percorso relativo dell'endpoint di riconciliazione sulla Piattaforma Unitaria.
     * <p>
     * Questo e' il path che il middleware usa per inoltrare la richiesta alla PU,
     * NON il path su cui il middleware riceve le richieste dai SIL.
     * I SIL invocano {@code /ws/pivot/PagamentiTelematiciPagatiRiconciliati} sul middleware;
     * il middleware inoltra a {@code {baseUrl}/pu/sil/soap/reconciliation/...} sulla PU.
     */
    static final String PLATFORM_RECONCILIATION_PATH =
            "/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati";

    /**
     * Tipo di operazione SOAP gestita da questo endpoint.
     * Corrisponde al local part del messaggio SOAP nel body.
     */
    static final String TIPO_OPERAZIONE = "pivotSILAutorizzaImportFlussoTesoreria";

    /**
     * DocumentBuilderFactory sicura (thread-safe dopo configurazione).
     */
    private final DocumentBuilderFactory secureDocumentBuilderFactory;

    /**
     * TransformerFactory sicura (thread-safe dopo configurazione).
     */
    private final TransformerFactory secureTransformerFactory;

    private final PiattaformaUnitariaClient piattaformaClient;
    private final ProxyForwardingClient proxyForwardingClient;
    private final RoutingDecisionService routingDecisionService;
    private final TransactionLoggingService transactionLoggingService;
    private final MiddlewareMetricsService metricsService;

    /**
     * Crea l'endpoint con tutte le dipendenze necessarie per il routing,
     * il logging transazionale e la raccolta metriche.
     *
     * @param piattaformaClient        client per l'inoltro verso la PU (con OAuth2)
     * @param proxyForwardingClient    client per il forward trasparente verso i backend legacy
     * @param routingDecisionService   servizio di decisione del routing
     * @param transactionLoggingService servizio per il logging transazionale su DB
     * @param metricsService           servizio per la raccolta metriche Micrometer
     */
    public ReconciliationEndpoint(PiattaformaUnitariaClient piattaformaClient,
                                  ProxyForwardingClient proxyForwardingClient,
                                  RoutingDecisionService routingDecisionService,
                                  TransactionLoggingService transactionLoggingService,
                                  MiddlewareMetricsService metricsService) {
        this.piattaformaClient = piattaformaClient;
        this.proxyForwardingClient = proxyForwardingClient;
        this.routingDecisionService = routingDecisionService;
        this.transactionLoggingService = transactionLoggingService;
        this.metricsService = metricsService;
        this.secureDocumentBuilderFactory = createSecureDocumentBuilderFactory();
        this.secureTransformerFactory = createSecureTransformerFactory();
    }

    /**
     * Gestisce la richiesta SOAP pivotSILAutorizzaImportFlussoTesoreria.
     *
     * <p>Estrae {@code codIpaEnte} dall'Header SOAP, consulta il
     * {@link RoutingDecisionService} per determinare la destinazione, e instrada
     * la richiesta verso la PU (con OAuth2) o verso il backend legacy (forward diretto).
     *
     * @param requestPayload l'elemento XML del body (usato per il routing Spring WS)
     * @param messageContext il contesto del messaggio SOAP con l'Envelope completo
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoTesoreria")
    @ResponsePayload
    public Element handleReconciliationRequest(@RequestPayload Element requestPayload,
                                               MessageContext messageContext) {

        log.info("Ricevuta richiesta SOAP di riconciliazione. LocalName: {}",
                requestPayload.getLocalName());

        // Variabili per il logging/metriche — devono sopravvivere al try-catch
        long startTime = System.currentTimeMillis();
        String codIpaEnte = null;
        String requestPath = null;
        RoutingDecision decision = null;

        try {
            // Estrai l'intero SOAP Envelope dal MessageContext
            String fullSoapEnvelope = extractFullSoapEnvelope(messageContext);
            log.debug("SOAP Envelope completo:\n{}", fullSoapEnvelope);

            // Estrai codIpaEnte dall'Header SOAP
            codIpaEnte = extractCodIpaEnte(fullSoapEnvelope);
            log.info("codIpaEnte estratto dall'Header SOAP: '{}'", codIpaEnte);

            // Determina il path HTTP della richiesta originale
            requestPath = extractRequestPath();
            log.debug("Path HTTP della richiesta: '{}'", requestPath);

            // Decisione di routing: PU o legacy?
            decision = routingDecisionService.decide(
                    codIpaEnte, TIPO_OPERAZIONE, requestPath);

            // Instrada in base alla decisione
            String responseXml;
            if (decision.isPiattaformaUnitaria()) {
                log.info("Routing verso PIATTAFORMA_UNITARIA per ente '{}'", codIpaEnte);
                responseXml = piattaformaClient.forwardSoapRequest(
                        PLATFORM_RECONCILIATION_PATH, fullSoapEnvelope);
            } else {
                log.info("Routing verso backend LEGACY ({}) per ente '{}'",
                        decision.getDestinazione(), codIpaEnte);
                responseXml = proxyForwardingClient.forwardToLegacyBackend(
                        decision.getDestinazione(), requestPath, fullSoapEnvelope);
            }

            log.info("Risposta ricevuta dal backend per la riconciliazione (modalita: {})",
                    decision.getModalita());
            log.debug("Risposta completa:\n{}", responseXml);

            // Estrai il contenuto del Body dalla risposta SOAP Envelope
            Element responseElement = extractBodyContent(responseXml);

            // Registra successo nel log transazionale e nelle metriche
            long durataMs = System.currentTimeMillis() - startTime;
            transactionLoggingService.logSuccesso(
                    codIpaEnte, TIPO_OPERAZIONE, decision, requestPath, 200, durataMs);
            metricsService.registraSuccesso(codIpaEnte, TIPO_OPERAZIONE, decision, durataMs);

            return responseElement;

        } catch (Exception e) {
            // Registra errore nel log transazionale e nelle metriche
            long durataMs = System.currentTimeMillis() - startTime;
            if (decision != null) {
                transactionLoggingService.logErrore(
                        codIpaEnte, TIPO_OPERAZIONE, decision, requestPath,
                        null, e.getMessage(), durataMs);
            } else {
                transactionLoggingService.logErrorePreRouting(
                        codIpaEnte, TIPO_OPERAZIONE, requestPath,
                        e.getMessage(), durataMs);
            }
            metricsService.registraErrore(codIpaEnte, TIPO_OPERAZIONE, decision, durataMs);

            // Le eccezioni di routing (EnteNonCensitoException, PathNonRiconosciutoException)
            // e di comunicazione vengono propagate al SoapFaultExceptionResolver
            log.error("Errore nella gestione della richiesta di riconciliazione: {}",
                    e.getMessage(), e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(
                    "Errore nell'elaborazione della richiesta di riconciliazione", e);
        }
    }

    /**
     * Estrae il codice IPA dell'ente dall'Header SOAP.
     *
     * <p>Cerca l'elemento {@code <codIpaEnte>} all'interno dell'elemento
     * {@code <intestazionePPT>} nell'Header SOAP. L'Header usa il namespace
     * {@code http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead}.
     *
     * @param soapEnvelope il SOAP Envelope completo come stringa XML
     * @return il valore del codice IPA dell'ente
     * @throws IllegalStateException se l'Header non contiene codIpaEnte
     */
    String extractCodIpaEnte(String soapEnvelope) throws Exception {
        Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(soapEnvelope)));

        // Cerca codIpaEnte in qualsiasi namespace (puo' essere nel namespace ppthead o senza)
        NodeList codIpaEnteNodes = document.getElementsByTagName("codIpaEnte");
        if (codIpaEnteNodes.getLength() > 0) {
            String codIpaEnte = codIpaEnteNodes.item(0).getTextContent().trim();
            if (!codIpaEnte.isEmpty()) {
                return codIpaEnte;
            }
        }

        // Tentativo con namespace esplicito
        NodeList nsNodes = document.getElementsByTagNameNS(HEADER_NAMESPACE_URI, "codIpaEnte");
        if (nsNodes.getLength() > 0) {
            String codIpaEnte = nsNodes.item(0).getTextContent().trim();
            if (!codIpaEnte.isEmpty()) {
                return codIpaEnte;
            }
        }

        throw new IllegalStateException(
                "codIpaEnte non trovato nell'Header SOAP. "
                + "L'Header deve contenere <intestazionePPT><codIpaEnte>...</codIpaEnte></intestazionePPT>");
    }

    /**
     * Estrae il path HTTP della richiesta corrente dal TransportContext di Spring WS.
     *
     * <p>Utilizza il {@link TransportContextHolder} per accedere alla connessione HTTP
     * sottostante e recuperare il path della richiesta originale.
     *
     * @return il path HTTP della richiesta (es. {@code /ws/pivot/PagamentiTelematici...})
     */
    String extractRequestPath() {
        TransportContext transportContext = TransportContextHolder.getTransportContext();
        if (transportContext != null && transportContext.getConnection() instanceof HttpServletConnection httpConn) {
            return httpConn.getHttpServletRequest().getRequestURI();
        }
        // Fallback: se non e' disponibile il TransportContext, usa un path di default
        log.warn("TransportContext non disponibile — impossibile determinare il path HTTP. "
                + "Uso del path di default /ws/pivot");
        return "/ws/pivot";
    }

    /**
     * Estrae l'intero SOAP Envelope serializzato dal MessageContext.
     *
     * @param messageContext il contesto del messaggio SOAP
     * @return la stringa XML dell'intero SOAP Envelope
     * @throws Exception in caso di errore nella serializzazione
     */
    private String extractFullSoapEnvelope(MessageContext messageContext) throws Exception {
        SoapMessage soapMessage = (SoapMessage) messageContext.getRequest();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        soapMessage.writeTo(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Estrae il contenuto del Body da un SOAP Envelope di risposta.
     *
     * <p>Il backend (PU o legacy) restituisce un SOAP Envelope completo, ma Spring WS
     * si aspetta solo il contenuto del Body come valore di ritorno (lo re-incapsulera'
     * automaticamente in un nuovo Envelope di risposta).
     *
     * @param soapEnvelope la risposta SOAP Envelope completa dal backend
     * @return l'elemento figlio del Body SOAP
     * @throws Exception in caso di errore nel parsing
     */
    private Element extractBodyContent(String soapEnvelope) throws Exception {
        Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(soapEnvelope)));

        Element root = document.getDocumentElement();

        // Cerca l'elemento Body nel SOAP Envelope
        var bodyNodes = root.getElementsByTagNameNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "Body");

        if (bodyNodes.getLength() == 0) {
            // Se non e' un Envelope SOAP, potrebbe essere direttamente il contenuto
            log.warn("La risposta dal backend non contiene un SOAP Envelope. " +
                    "Si restituisce il documento root come risposta.");
            return root;
        }

        Element bodyElement = (Element) bodyNodes.item(0);

        // Restituisci il primo elemento figlio del Body (il payload di risposta)
        var children = bodyElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                return (Element) children.item(i);
            }
        }

        // Fallback: se il Body e' vuoto o contiene solo testo, restituisci il Body stesso
        log.warn("Il Body SOAP della risposta non contiene elementi figli. " +
                "Si restituisce il Body stesso.");
        return bodyElement;
    }

    /**
     * Converte un elemento DOM in stringa XML.
     *
     * @param element l'elemento DOM da convertire
     * @return la rappresentazione stringa dell'elemento XML
     * @throws TransformerException in caso di errore nella trasformazione
     */
    private String elementToString(Element element) throws TransformerException {
        Transformer transformer = secureTransformerFactory.newTransformer();
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Converte una stringa XML in un elemento DOM con protezione XXE.
     *
     * @param xml la stringa XML da convertire
     * @return l'elemento DOM risultante
     * @throws Exception in caso di errore nel parsing
     */
    private Element stringToElement(String xml) throws Exception {
        Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        return document.getDocumentElement();
    }

    /**
     * Crea una DocumentBuilderFactory sicura con protezione XXE.
     * <p>
     * Previene:
     * - XML External Entity (XXE) injection
     * - Server-Side Request Forgery (SSRF) via entity esterne
     * - Denial of Service (DoS) via DTD entity expansion ("Billion Laughs")
     */
    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        try {
            // Disabilita DTD completamente
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // Disabilita entity esterne
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            // Disabilita XInclude
            factory.setXIncludeAware(false);
            // Disabilita entity expansion
            factory.setExpandEntityReferences(false);
        } catch (Exception e) {
            LoggerFactory.getLogger(ReconciliationEndpoint.class)
                    .warn("Alcune funzionalita di sicurezza XML non sono supportate dal parser: {}",
                            e.getMessage());
        }

        return factory;
    }

    /**
     * Crea una TransformerFactory sicura che previene attacchi via XSLT.
     */
    private static TransformerFactory createSecureTransformerFactory() {
        TransformerFactory factory = TransformerFactory.newInstance();
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (Exception e) {
            LoggerFactory.getLogger(ReconciliationEndpoint.class)
                    .warn("Alcune funzionalita di sicurezza TransformerFactory non sono supportate: {}",
                            e.getMessage());
        }
        return factory;
    }
}
