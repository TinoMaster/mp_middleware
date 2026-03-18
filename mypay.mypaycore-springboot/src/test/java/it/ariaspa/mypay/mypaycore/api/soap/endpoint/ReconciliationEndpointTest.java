package it.ariaspa.mypay.mypaycore.api.soap.endpoint;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per ReconciliationEndpoint.
 *
 * Verifica:
 * - Ricezione della richiesta SOAP e inoltro dell'intero Envelope al client
 * - Estrazione corretta del body dalla risposta SOAP Envelope della PU
 * - Gestione degli errori (eccezioni dal client)
 * - Utilizzo dei namespace corretti della PU (veneto, non lombardia)
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationEndpointTest {

    @Mock
    private PiattaformaUnitariaClient piattaformaClient;

    @Mock
    private MessageContext messageContext;

    @Mock
    private SoapMessage soapMessage;

    private ReconciliationEndpoint endpoint;

    /** SOAP Envelope di esempio come verrebbe inviato dal SIL */
    private static final String TEST_SOAP_ENVELOPE =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "xmlns:ppt=\"http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead\" " +
            "xmlns:ente=\"http://www.regione.veneto.it/pagamenti/pivot/ente/\">" +
            "<soapenv:Header>" +
            "<ppt:intestazionePPT><codIpaEnte>SELC_99999000013</codIpaEnte></ppt:intestazionePPT>" +
            "</soapenv:Header>" +
            "<soapenv:Body>" +
            "<ente:pivotSILAutorizzaImportFlussoTesoreria>" +
            "<password>BERGAMO</password><tipoFlusso>O</tipoFlusso>" +
            "</ente:pivotSILAutorizzaImportFlussoTesoreria>" +
            "</soapenv:Body>" +
            "</soapenv:Envelope>";

    @BeforeEach
    void setUp() {
        endpoint = new ReconciliationEndpoint(piattaformaClient);
    }

    /**
     * Configura il mock del MessageContext per restituire il SOAP Envelope completo.
     */
    private void setupMessageContextMock(String soapEnvelope) throws Exception {
        when(messageContext.getRequest()).thenReturn(soapMessage);
        doAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(0);
            out.write(soapEnvelope.getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(soapMessage).writeTo(any(java.io.OutputStream.class));
    }

    @Test
    @DisplayName("handleReconciliationRequest - inoltra l'Envelope completo e restituisce il body della risposta")
    void handleReconciliationRequest_success() throws Exception {
        // Risposta SOAP Envelope dalla PU
        String puResponseEnvelope =
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "xmlns:ente=\"http://www.regione.veneto.it/pagamenti/pivot/ente/\">" +
                "<soapenv:Header/>" +
                "<soapenv:Body>" +
                "<ente:pivotSILAutorizzaImportFlussoTesoreriaRisposta>" +
                "<esito>OK</esito>" +
                "</ente:pivotSILAutorizzaImportFlussoTesoreriaRisposta>" +
                "</soapenv:Body>" +
                "</soapenv:Envelope>";

        setupMessageContextMock(TEST_SOAP_ENVELOPE);

        when(piattaformaClient.forwardSoapRequest(
                eq(ReconciliationEndpoint.PLATFORM_RECONCILIATION_PATH), anyString()))
                .thenReturn(puResponseEnvelope);

        Element requestElement = createTestElement(
                "pivotSILAutorizzaImportFlussoTesoreria",
                ReconciliationEndpoint.NAMESPACE_URI,
                "<password>BERGAMO</password><tipoFlusso>O</tipoFlusso>");

        Element responseElement = endpoint.handleReconciliationRequest(requestElement, messageContext);

        assertNotNull(responseElement);
        assertEquals("pivotSILAutorizzaImportFlussoTesoreriaRisposta", responseElement.getLocalName());
        // Verifica che il client sia stato chiamato con l'Envelope completo (non solo il body)
        verify(piattaformaClient, times(1))
                .forwardSoapRequest(eq(ReconciliationEndpoint.PLATFORM_RECONCILIATION_PATH),
                        contains("soapenv:Envelope"));
    }

    @Test
    @DisplayName("handleReconciliationRequest - l'Envelope inoltrato contiene l'Header con codIpaEnte")
    void handleReconciliationRequest_forwardsFullEnvelopeWithHeader() throws Exception {
        String puResponseEnvelope =
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<soapenv:Header/>" +
                "<soapenv:Body>" +
                "<risposta><esito>OK</esito></risposta>" +
                "</soapenv:Body>" +
                "</soapenv:Envelope>";

        setupMessageContextMock(TEST_SOAP_ENVELOPE);

        when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                .thenReturn(puResponseEnvelope);

        Element requestElement = createTestElement(
                "pivotSILAutorizzaImportFlussoTesoreria",
                ReconciliationEndpoint.NAMESPACE_URI,
                "");

        endpoint.handleReconciliationRequest(requestElement, messageContext);

        // Cattura l'argomento inviato al client per verificare che contenga l'Header
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(piattaformaClient).forwardSoapRequest(anyString(), captor.capture());
        String forwardedEnvelope = captor.getValue();

        assertTrue(forwardedEnvelope.contains("codIpaEnte"),
                "L'Envelope inoltrato deve contenere codIpaEnte dall'Header SOAP");
        assertTrue(forwardedEnvelope.contains("intestazionePPT"),
                "L'Envelope inoltrato deve contenere intestazionePPT nell'Header");
    }

    @Test
    @DisplayName("handleReconciliationRequest - lancia RuntimeException su errore del client")
    void handleReconciliationRequest_throwsRuntimeException_onClientError() throws Exception {
        setupMessageContextMock(TEST_SOAP_ENVELOPE);

        when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                .thenThrow(new RuntimeException("Errore di comunicazione"));

        Element requestElement = createTestElement(
                "pivotSILAutorizzaImportFlussoTesoreria",
                ReconciliationEndpoint.NAMESPACE_URI,
                "<password>BERGAMO</password>");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> endpoint.handleReconciliationRequest(requestElement, messageContext));

        assertTrue(ex.getMessage().contains("riconciliazione"));
    }

    @Test
    @DisplayName("handleReconciliationRequest - preserva il namespace veneto nella risposta")
    void handleReconciliationRequest_preservesNamespace() throws Exception {
        String puResponseEnvelope =
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "xmlns:ente=\"http://www.regione.veneto.it/pagamenti/pivot/ente/\">" +
                "<soapenv:Header/>" +
                "<soapenv:Body>" +
                "<ente:risposta><ente:codice>OK</ente:codice></ente:risposta>" +
                "</soapenv:Body>" +
                "</soapenv:Envelope>";

        setupMessageContextMock(TEST_SOAP_ENVELOPE);

        when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                .thenReturn(puResponseEnvelope);

        Element requestElement = createTestElement(
                "pivotSILAutorizzaImportFlussoTesoreria",
                ReconciliationEndpoint.NAMESPACE_URI,
                "");

        Element responseElement = endpoint.handleReconciliationRequest(requestElement, messageContext);

        assertNotNull(responseElement);
        assertEquals("http://www.regione.veneto.it/pagamenti/pivot/ente/",
                responseElement.getNamespaceURI());
    }

    @Test
    @DisplayName("NAMESPACE_URI - utilizza il namespace corretto della PU (veneto)")
    void namespaceUri_isCorrectVenetoNamespace() {
        assertEquals("http://www.regione.veneto.it/pagamenti/pivot/ente/",
                ReconciliationEndpoint.NAMESPACE_URI);
    }

    @Test
    @DisplayName("HEADER_NAMESPACE_URI - utilizza il namespace corretto per l'header (ppthead)")
    void headerNamespaceUri_isCorrectPptheadNamespace() {
        assertEquals("http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead",
                ReconciliationEndpoint.HEADER_NAMESPACE_URI);
    }

    /**
     * Helper: crea un elemento DOM di test con il nome, namespace e contenuto specificati.
     */
    private Element createTestElement(String localName, String namespace, String innerXml)
            throws Exception {
        String xml = "<" + localName + " xmlns=\"" + namespace + "\">" + innerXml + "</" + localName + ">";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        return doc.getDocumentElement();
    }
}
