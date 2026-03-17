package it.ariaspa.mypay.mypaycore.api.soap.endpoint;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per ReconciliationEndpoint.
 *
 * Verifica:
 * - Ricezione della richiesta SOAP e inoltro al client
 * - Conversione corretta DOM <-> String
 * - Gestione degli errori (eccezioni dal client)
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationEndpointTest {

    @Mock
    private PiattaformaUnitariaClient piattaformaClient;

    private ReconciliationEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new ReconciliationEndpoint(piattaformaClient);
    }

    @Test
    @DisplayName("handleReconciliationRequest - inoltra la richiesta e restituisce la risposta")
    void handleReconciliationRequest_success() throws Exception {
        String responseXml = "<pivotSILAutorizzaImportFlussoTesoreriaRisposta " +
                "xmlns=\"http://www.regione.lombardia.it/mypay/ente\">" +
                "<esito>OK</esito>" +
                "</pivotSILAutorizzaImportFlussoTesoreriaRisposta>";

        when(piattaformaClient.forwardSoapRequest(
                eq(ReconciliationEndpoint.PLATFORM_RECONCILIATION_PATH), anyString()))
                .thenReturn(responseXml);

        Element requestElement = createTestElement(
                "pivotSILAutorizzaImportFlussoTesoreria",
                ReconciliationEndpoint.NAMESPACE_URI,
                "<codIpaEnte>IPA_TEST</codIpaEnte>");

        Element responseElement = endpoint.handleReconciliationRequest(requestElement);

        assertNotNull(responseElement);
        assertEquals("pivotSILAutorizzaImportFlussoTesoreriaRisposta", responseElement.getLocalName());
        verify(piattaformaClient, times(1))
                .forwardSoapRequest(eq(ReconciliationEndpoint.PLATFORM_RECONCILIATION_PATH), anyString());
    }

    @Test
    @DisplayName("handleReconciliationRequest - lancia RuntimeException su errore del client")
    void handleReconciliationRequest_throwsRuntimeException_onClientError() throws Exception {
        when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                .thenThrow(new RuntimeException("Errore di comunicazione"));

        Element requestElement = createTestElement(
                "pivotSILAutorizzaImportFlussoTesoreria",
                ReconciliationEndpoint.NAMESPACE_URI,
                "<codIpaEnte>IPA_TEST</codIpaEnte>");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> endpoint.handleReconciliationRequest(requestElement));

        assertTrue(ex.getMessage().contains("riconciliazione"));
    }

    @Test
    @DisplayName("handleReconciliationRequest - preserva il namespace nella risposta")
    void handleReconciliationRequest_preservesNamespace() throws Exception {
        String responseXml = "<ns:risposta xmlns:ns=\"http://www.regione.lombardia.it/mypay/ente\">" +
                "<ns:codice>OK</ns:codice>" +
                "</ns:risposta>";

        when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                .thenReturn(responseXml);

        Element requestElement = createTestElement(
                "pivotSILAutorizzaImportFlussoTesoreria",
                ReconciliationEndpoint.NAMESPACE_URI,
                "");

        Element responseElement = endpoint.handleReconciliationRequest(requestElement);

        assertNotNull(responseElement);
        assertEquals("http://www.regione.lombardia.it/mypay/ente",
                responseElement.getNamespaceURI());
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
