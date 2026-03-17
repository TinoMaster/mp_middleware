package it.ariaspa.mypay.mypaycore.api.soap.endpoint;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
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
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Endpoint SOAP per la riconciliazione dei pagamenti telematici.
 *
 * Questo endpoint riceve le richieste SOAP dai sistemi SIL (Sistemi Informativi Locali)
 * e le inoltra alla Piattaforma Unitaria tramite il PiattaformaUnitariaClient.
 *
 * Flusso:
 * 1. Il SIL invia una richiesta SOAP a questo endpoint
 * 2. L'endpoint estrae il payload XML dalla richiesta
 * 3. La richiesta viene inoltrata alla Piattaforma Unitaria (autenticata tramite OAuth2)
 * 4. La risposta della piattaforma viene restituita al SIL
 *
 * Endpoint URI: /pu/sil/soap/reconciliation
 * Esempio di richiesta: pivotSILAutorizzaImportFlussoTesoreria
 *
 * Sicurezza XML:
 * - Prevenzione XXE (XML External Entity) attacks
 * - Disabilitazione DTD e entity esterne nel parser XML
 * - TransformerFactory sicura senza accesso a DTD/stylesheet esterni
 *
 * Fase 1: Approccio contract-last semplificato.
 */
@Endpoint
public class ReconciliationEndpoint {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEndpoint.class);

    /**
     * Namespace URI per le operazioni di riconciliazione.
     * Da aggiornare con il namespace reale del WSDL della Piattaforma Unitaria.
     */
    static final String NAMESPACE_URI = "http://www.regione.lombardia.it/mypay/ente";

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
     * Riceve il payload XML dalla richiesta SOAP del SIL, lo converte in stringa,
     * lo inoltra alla Piattaforma Unitaria e restituisce la risposta come elemento DOM.
     *
     * @param requestPayload l'elemento XML della richiesta SOAP
     * @return l'elemento XML della risposta dalla Piattaforma Unitaria
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoTesoreria")
    @ResponsePayload
    public Element handleReconciliationRequest(@RequestPayload Element requestPayload) {

        log.info("Ricevuta richiesta SOAP di riconciliazione. LocalName: {}",
                requestPayload.getLocalName());

        try {
            // Converti il payload XML in stringa per l'inoltro
            String soapXml = elementToString(requestPayload);
            log.debug("Payload SOAP ricevuto dal SIL: {}", soapXml);

            // Inoltra alla Piattaforma Unitaria
            String responseXml = piattaformaClient.forwardSoapRequest(
                    PLATFORM_RECONCILIATION_PATH, soapXml);

            log.info("Risposta ricevuta dalla Piattaforma Unitaria per la riconciliazione");

            // Converti la risposta in elemento DOM per restituirla come SOAP response
            return stringToElement(responseXml);

        } catch (Exception e) {
            log.error("Errore nella gestione della richiesta di riconciliazione: {}",
                    e.getMessage(), e);
            throw new RuntimeException(
                    "Errore nell'elaborazione della richiesta di riconciliazione", e);
        }
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
