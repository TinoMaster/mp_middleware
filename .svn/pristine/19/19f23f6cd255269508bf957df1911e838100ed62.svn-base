package it.ariaspa.mypay.mypaycore.api.soap.endpoint;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient;
import it.ariaspa.mypay.mypaycore.api.common.exception.CredenzialeSilNonValidaException;
import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonIdentificabileException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import it.ariaspa.mypay.mypaycore.api.domain.EnteCompleto;
import it.ariaspa.mypay.mypaycore.api.logging.TransactionLoggingService;
import it.ariaspa.mypay.mypaycore.api.metrics.MiddlewareMetricsService;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.context.MessageContext;
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
import java.util.Optional;

/**
 * Classe base astratta per tutti gli endpoint SOAP del middleware (proxy trasparente).
 *
 * <p>Concentra tutta la logica comune condivisa tra gli endpoint di mypay e mypivot:
 * <ul>
 *   <li>Estrazione del SOAP Envelope dal {@link MessageContext}</li>
 *   <li>Estrazione dell'identificativo dell'ente (codIpaEnte o identificativoDominio)
 *       dall'Envelope SOAP tramite ricerca generica XML</li>
 *   <li>Risoluzione del codice fiscale ({@code identificativoDominio}) al codice IPA
 *       tramite {@link EnteCacheService#findByCodiceFiscale(String)}</li>
 *   <li>Estrazione del path HTTP della richiesta corrente</li>
 *   <li>Routing della richiesta (PU con OAuth2 o legacy diretto)</li>
 *   <li>Estrazione del Body dalla risposta SOAP</li>
 *   <li>Logging transazionale e metriche Micrometer</li>
 *   <li>Protezione XXE (XML External Entity) su tutti i parser XML</li>
 * </ul>
 *
 * <p>Ogni sottoclasse concreta deve:
 * <ol>
 *   <li>Definire le costanti {@code NAMESPACE_URI} e il path PU</li>
 *   <li>Implementare {@link #getDefaultPath()} con il path di fallback dell'endpoint</li>
 *   <li>Implementare {@link #getFaultDetailNamespace()} con il namespace XML corretto
 *       per i fault detail (garantisce che i SOAP Fault rispettino il contratto WSDL)</li>
 *   <li>Definire i metodi {@code @PayloadRoot} per ogni operazione SOAP</li>
 *   <li>Delegare la gestione della richiesta a {@link #processRequest(Element, MessageContext, String)}</li>
 * </ol>
 *
 * <p>Sicurezza XML:
 * <ul>
 *   <li>Prevenzione XXE (XML External Entity) injection</li>
 *   <li>Disabilitazione DTD e entity esterne nel parser XML</li>
 *   <li>TransformerFactory sicura senza accesso a DTD/stylesheet esterni</li>
 * </ul>
 *
 * @see it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypivot.ReconciliationEndpoint
 * @see it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypay.PagamentiTelematiciDovutiPagatiEndpoint
 */
public abstract class AbstractSoapProxyEndpoint {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** DocumentBuilderFactory sicura (thread-safe dopo configurazione). */
    private final DocumentBuilderFactory secureDocumentBuilderFactory;

    /** TransformerFactory sicura (thread-safe dopo configurazione). */
    private final TransformerFactory secureTransformerFactory;

    /** Client per l'inoltro verso la PU (con OAuth2 per-ente). */
    protected final PiattaformaUnitariaClient piattaformaClient;

    /** Client per il forward trasparente verso i backend legacy. */
    protected final ProxyForwardingClient proxyForwardingClient;

    /** Servizio di decisione del routing. */
    protected final RoutingDecisionService routingDecisionService;

    /** Servizio per il logging transazionale su DB. */
    protected final TransactionLoggingService transactionLoggingService;

    /** Servizio per la raccolta metriche Micrometer. */
    protected final MiddlewareMetricsService metricsService;

    /** Cache degli enti con lookup duale (per codIpaEnte e per codiceFiscale). */
    protected final EnteCacheService enteCacheService;

    /**
     * Costruttore protetto per le sottoclassi.
     *
     * @param piattaformaClient        client per l'inoltro verso la PU
     * @param proxyForwardingClient    client per il forward verso i backend legacy
     * @param routingDecisionService   servizio di decisione del routing
     * @param transactionLoggingService servizio per il logging transazionale su DB
     * @param metricsService           servizio per la raccolta metriche Micrometer
     * @param enteCacheService         cache degli enti con lookup duale
     */
    protected AbstractSoapProxyEndpoint(PiattaformaUnitariaClient piattaformaClient,
                                        ProxyForwardingClient proxyForwardingClient,
                                        RoutingDecisionService routingDecisionService,
                                        TransactionLoggingService transactionLoggingService,
                                        MiddlewareMetricsService metricsService,
                                        EnteCacheService enteCacheService) {
        this.piattaformaClient = piattaformaClient;
        this.proxyForwardingClient = proxyForwardingClient;
        this.routingDecisionService = routingDecisionService;
        this.transactionLoggingService = transactionLoggingService;
        this.metricsService = metricsService;
        this.enteCacheService = enteCacheService;
        this.secureDocumentBuilderFactory = createSecureDocumentBuilderFactory();
        this.secureTransformerFactory = createSecureTransformerFactory();
    }

    // =====================================================================
    // Metodo principale di processamento — da invocare dalle sottoclassi
    // =====================================================================

    /**
     * Processa una richiesta SOAP: estrae l'ente, decide il routing, inoltra e restituisce.
     *
     * <p>Questo e' il metodo centrale invocato da ogni operazione {@code @PayloadRoot}
     * nelle sottoclassi. Esegue il flusso completo:
     * <ol>
     *   <li>Estrae il SOAP Envelope dal MessageContext</li>
     *   <li>Identifica l'ente (per codIpaEnte o identificativoDominio)</li>
     *   <li>Determina il path HTTP della richiesta</li>
     *   <li>Decide il routing (PU o legacy)</li>
     *   <li>Inoltra la richiesta al backend selezionato</li>
     *   <li>Registra successo/errore nel log transazionale e nelle metriche</li>
     * </ol>
     *
     * @param requestPayload il payload XML della richiesta (elemento body)
     * @param messageContext il contesto del messaggio SOAP
     * @param platformPath   il path relativo per l'inoltro alla PU
     *                       (es. {@code /pu/sil/soap/pa/PagamentiTelematiciDovutiPagati})
     * @return l'elemento XML della risposta dal backend
     */
    protected Element processRequest(Element requestPayload,
                                     MessageContext messageContext,
                                     String platformPath) {

        String operationName = requestPayload.getLocalName();
        log.info("Ricevuta richiesta SOAP: operazione='{}', endpoint='{}'",
                operationName, getClass().getSimpleName());

        long startTime = System.currentTimeMillis();
        String codIpaEnte = null;
        String requestPath = null;
        RoutingDecision decision = null;

        try {
            // Estrai l'intero SOAP Envelope dal MessageContext
            String fullSoapEnvelope = extractFullSoapEnvelope(messageContext);
            log.debug("SOAP Envelope completo:\n{}", fullSoapEnvelope);

            // Estrai l'identificativo dell'ente (ricerca generica nel SOAP Envelope)
            codIpaEnte = extractEnteIdentifier(fullSoapEnvelope);
            log.info("codIpaEnte identificato: '{}' per operazione '{}'", codIpaEnte, operationName);

            // Verifica le credenziali del SIL: confronta la <password> del body SOAP
            // con quella configurata in mygov_ente.de_password per questo ente.
            verificaCredenzialeSil(fullSoapEnvelope, codIpaEnte);

            // Determina il path HTTP della richiesta originale
            requestPath = extractRequestPath();
            log.debug("Path HTTP della richiesta: '{}'", requestPath);

            // Decisione di routing: PU o legacy?
            decision = routingDecisionService.decide(codIpaEnte, requestPath);

            // Instrada in base alla decisione
            String responseXml;
            if (decision.isPiattaformaUnitaria()) {
                log.info("Routing verso PIATTAFORMA_UNITARIA per ente '{}', operazione '{}'",
                        codIpaEnte, operationName);
                responseXml = piattaformaClient.forwardSoapRequest(
                        platformPath, fullSoapEnvelope, decision.getEnte());
            } else {
                log.info("Routing verso backend LEGACY ({}) per ente '{}', operazione '{}'",
                        decision.getDestinazione(), codIpaEnte, operationName);
                responseXml = proxyForwardingClient.forwardToLegacyBackend(
                        decision.getDestinazione(), requestPath, fullSoapEnvelope);
            }

            log.info("Risposta ricevuta dal backend per '{}' (modalita': {})",
                    operationName, decision.getModalita());
            log.debug("Risposta completa:\n{}", responseXml);

            // Verifica se il backend ha risposto con un SOAP Fault.
            // Se si', lo traduciamo in una PiattaformaCommunicationException per evitare:
            // 1. Propagazione di dettagli interni del backend ai SIL (information leakage)
            // 2. Incoerenza tra namespace del fault del backend e namespace del middleware
            String faultString = estraiSoapFaultString(responseXml);
            if (faultString != null) {
                log.warn("Il backend ha risposto con un SOAP Fault per '{}': {}", operationName, faultString);
                throw new PiattaformaCommunicationException(
                        "Il backend ha risposto con un errore: " + faultString);
            }

            // Estrai il contenuto del Body dalla risposta SOAP Envelope
            Element responseElement = extractBodyContent(responseXml);

            // Registra successo nel log transazionale e nelle metriche
            long durataMs = System.currentTimeMillis() - startTime;
            transactionLoggingService.logSuccesso(codIpaEnte, operationName, decision, requestPath, 200, durataMs);
            metricsService.registraSuccesso(codIpaEnte, decision, durataMs);

            return responseElement;

        } catch (Exception e) {
            // Registra errore nel log transazionale e nelle metriche
            long durataMs = System.currentTimeMillis() - startTime;
            if (decision != null) {
                transactionLoggingService.logErrore(
                        codIpaEnte, operationName, decision, requestPath, null, e.getMessage(), durataMs);
            } else {
                transactionLoggingService.logErrorePreRouting(
                        codIpaEnte, operationName, requestPath, e.getMessage(), durataMs);
            }
            metricsService.registraErrore(codIpaEnte, decision, durataMs);

            log.error("Errore nella gestione della richiesta '{}': {}",
                    operationName, e.getMessage(), e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(
                    "Errore nell'elaborazione della richiesta SOAP '" + operationName + "'", e);
        }
    }

    // =====================================================================
    // Estrazione dell'identificativo dell'ente — ricerca generica
    // =====================================================================

    /**
     * Verifica le credenziali del SIL confrontando la password estratta dal body SOAP
     * con quella configurata in {@code mygov_ente.de_password} per l'ente identificato.
     *
     * <p>Il campo {@code de_password} e' obbligatorio per ogni ente: se assente in cache
     * (caso anomalo) o se il tag {@code <password>} manca nel SOAP, la richiesta viene
     * sempre rifiutata.
     *
     * <p>La password non viene mai loggata, ne' in caso di successo ne' in caso di errore.
     *
     * @param soapEnvelope il SOAP Envelope completo come stringa XML
     * @param codIpaEnte   il codice IPA dell'ente gia' identificato
     * @throws CredenzialeSilNonValidaException se la password e' assente o non corrisponde
     */
    private void verificaCredenzialeSil(String soapEnvelope, String codIpaEnte) throws Exception {
        // Recupera l'ente dalla cache (gia' caricato e valido a questo punto del flusso)
        EnteCompleto enteCompleto = enteCacheService.findByCodIpaEnte(codIpaEnte)
                .orElseThrow(() -> new CredenzialeSilNonValidaException(codIpaEnte));

        // Estrai il tag <password> dal body SOAP
        Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(soapEnvelope)));
        String passwordRicevuta = extractTextFromTag(document, "password");

        // Tag <password> assente nella richiesta SOAP → rifiuto immediato
        if (passwordRicevuta == null) {
            log.warn("Credenziali SIL mancanti: tag <password> assente nella richiesta SOAP "
                    + "per ente '{}' — accesso negato", codIpaEnte);
            throw new CredenzialeSilNonValidaException(codIpaEnte);
        }

        // Confronto in chiaro tra password ricevuta e password configurata in DB.
        // La password non viene mai loggata per sicurezza.
        if (!passwordRicevuta.equals(enteCompleto.getEnte().getDePassword())) {
            log.warn("Credenziali SIL non valide per ente '{}' — accesso negato", codIpaEnte);
            throw new CredenzialeSilNonValidaException(codIpaEnte);
        }

        log.debug("Credenziali SIL verificate con successo per ente '{}'", codIpaEnte);
    }


    /**
     * Estrae l'identificativo dell'ente dal SOAP Envelope con ricerca generica.
     *
     * <p>Strategia di ricerca (in ordine di priorita'):
     * <ol>
     *   <li>Cerca {@code <codIpaEnte>} — usato da DovutiPagati e mypivot.
     *       Se trovato, e' gia' il codice IPA → restituito direttamente.</li>
     *   <li>Cerca {@code <identificativoDominio>} — usato da CCPPa, Esito, CCP,
     *       CCP25, RT, RP, AvvisiDigitali. Contiene il codice fiscale dell'ente →
     *       risolto al codice IPA tramite {@link EnteCacheService#findByCodiceFiscale(String)}.</li>
     * </ol>
     *
     * @param soapEnvelope il SOAP Envelope completo come stringa XML
     * @return il codice IPA dell'ente
     * @throws IllegalStateException se nessun identificativo viene trovato
     *         o se il codice fiscale non corrisponde a nessun ente censito
     */
    protected String extractEnteIdentifier(String soapEnvelope) throws Exception {
        Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(soapEnvelope)));

        // --- Tentativo 1: cerca <codIpaEnte> (qualsiasi namespace) ---
        String codIpaEnte = extractTextFromTag(document, "codIpaEnte");
        if (codIpaEnte != null) {
            return codIpaEnte;
        }

        // --- Tentativo 2: cerca <identificativoDominio> (codice fiscale) ---
        String identificativoDominio = extractTextFromTag(document, "identificativoDominio");
        if (identificativoDominio != null) {
            // Risolvi il codice fiscale al codice IPA tramite la cache duale
            Optional<EnteCompleto> ente = enteCacheService.findByCodiceFiscale(identificativoDominio);
            if (ente.isPresent()) {
                log.debug("identificativoDominio '{}' risolto a codIpaEnte '{}'",
                        identificativoDominio, ente.get().getCodIpaEnte());
                return ente.get().getCodIpaEnte();
            }

            // Codice fiscale non trovato nella cache — non censito o messaggio errato del SIL
            throw new EnteNonIdentificabileException(
                    "identificativoDominio (codice fiscale) '" + identificativoDominio
                    + "' non corrisponde a nessun ente censito in mygov_ente");
        }

        // Nessun identificatore ente nella richiesta SOAP — errore del SIL (fault Client)
        throw new EnteNonIdentificabileException(
                "Impossibile identificare l'ente dalla richiesta SOAP. "
                + "Nessun elemento <codIpaEnte> o <identificativoDominio> trovato nel SOAP Envelope.");
    }

    /**
     * Estrae il contenuto testuale di un tag XML cercandolo per nome locale
     * (senza namespace specifico).
     *
     * @param document il documento XML parsato
     * @param tagName  il nome locale del tag da cercare
     * @return il contenuto testuale del tag, o {@code null} se non trovato
     */
    private String extractTextFromTag(Document document, String tagName) {
        // Cerca senza namespace specifico (getElementsByTagName usa solo localName)
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String value = nodes.item(0).getTextContent().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    // =====================================================================
    // Estrazione SOAP Envelope e path HTTP
    // =====================================================================

    /**
     * Estrae l'intero SOAP Envelope serializzato dal MessageContext.
     *
     * @param messageContext il contesto del messaggio SOAP
     * @return la stringa XML dell'intero SOAP Envelope
     * @throws Exception in caso di errore nella serializzazione
     */
    protected String extractFullSoapEnvelope(MessageContext messageContext) throws Exception {
        SoapMessage soapMessage = (SoapMessage) messageContext.getRequest();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        soapMessage.writeTo(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    /**
     * Estrae il path HTTP della richiesta corrente dal TransportContext di Spring WS.
     *
     * @return il path HTTP della richiesta (es. {@code /ws/pa/PagamentiTelematici...})
     */
    protected String extractRequestPath() {
        TransportContext transportContext = TransportContextHolder.getTransportContext();
        if (transportContext != null
                && transportContext.getConnection() instanceof HttpServletConnection httpConn) {
            return httpConn.getHttpServletRequest().getRequestURI();
        }
        log.warn("TransportContext non disponibile — impossibile determinare il path HTTP. "
                + "Uso del path di default.");
        return getDefaultPath();
    }

    /**
     * Restituisce il path di default per l'endpoint (usato come fallback se il
     * TransportContext non e' disponibile). Le sottoclassi possono sovrascriverlo.
     *
     * @return il path di default (es. {@code /ws/pa}, {@code /ws/fesp}, {@code /ws/pivot})
     */
    protected abstract String getDefaultPath();

    /**
     * Restituisce il namespace XML da usare nell'elemento {@code <errorCode>} del fault detail
     * dei SOAP Fault generati dal middleware per questo endpoint.
     *
     * <p>Il namespace deve corrispondere al dominio semantico dell'endpoint per rispettare
     * i contratti WSDL originali di pagoPA:
     * <ul>
     *   <li>Endpoint MyPay (PA + FESP): {@code http://www.regione.veneto.it/pagamenti/ente/fault}</li>
     *   <li>Endpoint MyPivot: {@code http://www.regione.veneto.it/pagamenti/pivot/ente/fault}</li>
     * </ul>
     *
     * <p>Le costanti appropriate sono centralizzate in {@link it.ariaspa.mypay.mypaycore.api.util.Constants}
     * ({@code NS_FAULT_MYPAY} e {@code NS_FAULT_MYPIVOT}).
     *
     * @return il namespace URI per il fault detail di questo endpoint
     */
    public abstract String getFaultDetailNamespace();

    // =====================================================================
    // Estrazione del Body dalla risposta SOAP
    // =====================================================================

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
    protected Element extractBodyContent(String soapEnvelope) throws Exception {
        Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(soapEnvelope)));

        Element root = document.getDocumentElement();

        // Cerca l'elemento Body nel SOAP Envelope
        var bodyNodes = root.getElementsByTagNameNS(
                "http://schemas.xmlsoap.org/soap/envelope/", "Body");

        if (bodyNodes.getLength() == 0) {
            log.warn("La risposta dal backend non contiene un SOAP Envelope. "
                    + "Si restituisce il documento root come risposta.");
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

        log.warn("Il Body SOAP della risposta non contiene elementi figli. "
                + "Si restituisce il Body stesso.");
        return bodyElement;
    }

    // =====================================================================
    // Utility XML
    // =====================================================================

    /**
     * Verifica se un SOAP Envelope di risposta contiene un elemento {@code <Fault>}
     * nel Body, e in caso affermativo ne restituisce il testo del {@code <faultstring>}.
     *
     * <p>Usato da {@link #processRequest} per intercettare i SOAP Fault originati
     * dal backend (PU o legacy) prima di propagarli ai SIL. Il middleware li traduce
     * in una {@link PiattaformaCommunicationException} per garantire:
     * <ul>
     *   <li>Coerenza del namespace del fault detail (non si espone il namespace del backend)</li>
     *   <li>Nessun leakage di dettagli interni del backend verso i SIL</li>
     * </ul>
     *
     * @param soapEnvelope la risposta XML dal backend (SOAP Envelope completo)
     * @return il testo del {@code <faultstring>} se presente, {@code null} altrimenti
     */
    protected String estraiSoapFaultString(String soapEnvelope) {
        try {
            Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(soapEnvelope)));

            // Cerca <Fault> nel namespace SOAP 1.1
            var faultNodes = document.getElementsByTagNameNS(
                    "http://schemas.xmlsoap.org/soap/envelope/", "Fault");

            if (faultNodes.getLength() == 0) {
                // Prova anche senza namespace (alcune implementazioni omettono il namespace sul Fault)
                faultNodes = document.getElementsByTagName("Fault");
            }

            if (faultNodes.getLength() > 0) {
                Element faultElement = (Element) faultNodes.item(0);
                // Cerca <faultstring> dentro <Fault>
                var faultStringNodes = faultElement.getElementsByTagName("faultstring");
                if (faultStringNodes.getLength() > 0) {
                    return faultStringNodes.item(0).getTextContent().trim();
                }
                // Se non c'e' faultstring, restituisce un messaggio generico
                return "SOAP Fault ricevuto dal backend (faultstring non disponibile)";
            }

            return null; // Nessun Fault presente

        } catch (Exception e) {
            // Se non riusciamo a parsare la risposta, non blocchiamo il flusso normale.
            // La risposta potrebbe non essere XML o potrebbe essere malformata.
            log.debug("Impossibile verificare la presenza di SOAP Fault nella risposta: {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * Converte un elemento DOM in stringa XML.
     *
     * @param element l'elemento DOM da convertire
     * @return la rappresentazione stringa dell'elemento XML
     * @throws TransformerException in caso di errore nella trasformazione
     */
    protected String elementToString(Element element) throws TransformerException {
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
    protected Element stringToElement(String xml) throws Exception {
        Document document = secureDocumentBuilderFactory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        return document.getDocumentElement();
    }

    // =====================================================================
    // Factory sicure per XML parser e Transformer
    // =====================================================================

    /**
     * Crea una DocumentBuilderFactory sicura con protezione XXE.
     * <p>
     * Previene:
     * <ul>
     *   <li>XML External Entity (XXE) injection</li>
     *   <li>Server-Side Request Forgery (SSRF) via entity esterne</li>
     *   <li>Denial of Service (DoS) via DTD entity expansion ("Billion Laughs")</li>
     * </ul>
     */
    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (Exception e) {
            LoggerFactory.getLogger(AbstractSoapProxyEndpoint.class)
                    .warn("Alcune funzionalita' di sicurezza XML non sono supportate dal parser: {}",
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
            LoggerFactory.getLogger(AbstractSoapProxyEndpoint.class)
                    .warn("Alcune funzionalita' di sicurezza TransformerFactory non sono supportate: {}",
                            e.getMessage());
        }
        return factory;
    }
}
