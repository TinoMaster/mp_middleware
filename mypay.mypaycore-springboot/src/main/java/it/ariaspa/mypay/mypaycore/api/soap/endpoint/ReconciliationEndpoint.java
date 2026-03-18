package it.ariaspa.mypay.mypaycore.api.soap.endpoint;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
 * Endpoint SOAP per la riconciliazione dei pagamenti telematici.
 *
 * Questo endpoint riceve le richieste SOAP dai sistemi SIL (Sistemi Informativi Locali)
 * e le inoltra alla Piattaforma Unitaria tramite il PiattaformaUnitariaClient.
 *
 * Flusso:
 * 1. Il SIL invia una richiesta SOAP a questo endpoint
 * 2. L'endpoint cattura l'intero SOAP Envelope (Header + Body) dal MessageContext
 * 3. L'Envelope completo viene inoltrato alla Piattaforma Unitaria (autenticata tramite OAuth2)
 * 4. La risposta della piattaforma viene restituita al SIL
 *
 * IMPORTANTE: Il SIL invia nell'Header SOAP il codIpaEnte (namespace ppthead) che
 * identifica l'ente. La PU richiede l'Envelope completo con Header e Body.
 * Per questo motivo, l'endpoint utilizza il MessageContext per estrarre l'intero
 * messaggio SOAP e inoltrarlo alla PU cosi com'e (approccio transparent proxy).
 *
 * Endpoint URI: /pu/sil/soap/reconciliation
 * Esempio di richiesta: pivotSILAutorizzaImportFlussoTesoreria
 *
 * Namespace PU:
 * - Header: http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead
 * - Body:   http://www.regione.veneto.it/pagamenti/pivot/ente/
 *
 * Sicurezza XML:
 * - Prevenzione XXE (XML External Entity) attacks
 * - Disabilitazione DTD e entity esterne nel parser XML
 * - TransformerFactory sicura senza accesso a DTD/stylesheet esterni
 *
 * Fase 1: Approccio contract-last semplificato con transparent proxy.
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
     */
    static final String PLATFORM_RECONCILIATION_PATH =
            "/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati";

    /** DocumentBuilderFactory sicura (thread-safe dopo configurazione). */
    private final DocumentBuilderFactory secureDocumentBuilderFactory;

    /** TransformerFactory sicura (thread-safe dopo configurazione). */
    private final TransformerFactory secureTransformerFactory;

    private final PiattaformaUnitariaClient piattaformaClient;

    public ReconciliationEndpoint(PiattaformaUnitariaClient piattaformaClient) {
        this.piattaformaClient = piattaformaClient;
        this.secureDocumentBuilderFactory = createSecureDocumentBuilderFactory();
        this.secureTransformerFactory = createSecureTransformerFactory();
    }

    /**
     * Gestisce la richiesta SOAP pivotSILAutorizzaImportFlussoTesoreria.
     *
     * Cattura l'intero SOAP Envelope dal MessageContext (incluso l'Header con codIpaEnte),
     * lo inoltra alla Piattaforma Unitaria e restituisce la risposta come elemento DOM.
     *
     * Il parametro requestPayload viene usato da Spring WS per il routing (@PayloadRoot),
     * ma l'inoltro alla PU utilizza l'Envelope completo estratto dal MessageContext.
     *
     * @param requestPayload l'elemento XML del body (usato per il routing Spring WS)
     * @param messageContext il contesto del messaggio SOAP con l'Envelope completo
     * @return l'elemento XML della risposta dalla Piattaforma Unitaria
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoTesoreria")
    @ResponsePayload
    public Element handleReconciliationRequest(@RequestPayload Element requestPayload,
                                               MessageContext messageContext) {

        log.info("Ricevuta richiesta SOAP di riconciliazione. LocalName: {}",
                requestPayload.getLocalName());

        try {
            // Estrai l'intero SOAP Envelope dal MessageContext per inoltrarlo alla PU
            String fullSoapEnvelope = extractFullSoapEnvelope(messageContext);
            log.debug("SOAP Envelope completo da inoltrare alla PU:\n{}", fullSoapEnvelope);

            // Inoltra l'Envelope completo alla Piattaforma Unitaria
            String responseXml = piattaformaClient.forwardSoapRequest(
                    PLATFORM_RECONCILIATION_PATH, fullSoapEnvelope);

            log.info("Risposta ricevuta dalla Piattaforma Unitaria per la riconciliazione");
            log.debug("Risposta completa dalla PU:\n{}", responseXml);

            // La PU restituisce un SOAP Envelope completo.
            // Estraiamo il contenuto del Body per restituirlo tramite Spring WS
            // (che lo re-incapsulera in un nuovo Envelope di risposta).
            return extractBodyContent(responseXml);

        } catch (Exception e) {
            log.error("Errore nella gestione della richiesta di riconciliazione: {}",
                    e.getMessage(), e);
            throw new RuntimeException(
                    "Errore nell'elaborazione della richiesta di riconciliazione", e);
        }
    }

    /**
     * Estrae l'intero SOAP Envelope serializzato dal MessageContext.
     *
     * Utilizza SoapMessage.writeTo() per ottenere il messaggio SOAP completo
     * cosi come e stato ricevuto dal SIL (con Header e Body).
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
     * La PU restituisce un SOAP Envelope completo, ma Spring WS si aspetta
     * solo il contenuto del Body come valore di ritorno (lo re-incapsulera
     * automaticamente in un nuovo Envelope di risposta).
     *
     * @param soapEnvelope la risposta SOAP Envelope completa dalla PU
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
            // Se non e un Envelope SOAP, potrebbe essere direttamente il contenuto
            log.warn("La risposta dalla PU non contiene un SOAP Envelope. " +
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

        // Fallback: se il Body e vuoto o contiene solo testo, restituisci il Body stesso
        log.warn("Il Body SOAP della risposta PU non contiene elementi figli. " +
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
     *
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
