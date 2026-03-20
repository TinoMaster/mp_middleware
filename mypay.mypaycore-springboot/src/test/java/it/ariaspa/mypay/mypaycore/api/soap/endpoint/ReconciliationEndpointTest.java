package it.ariaspa.mypay.mypaycore.api.soap.endpoint;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient;
import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.logging.TransactionLoggingService;
import it.ariaspa.mypay.mypaycore.api.metrics.MiddlewareMetricsService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import jakarta.servlet.http.HttpServletRequest;
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
 * <p>Verifica:
 * <ul>
 *   <li>Routing verso Piattaforma Unitaria (ente migrato alla PU)</li>
 *   <li>Routing verso backend legacy (ente non migrato)</li>
 *   <li>Estrazione corretta di codIpaEnte dall'Header SOAP</li>
 *   <li>Propagazione delle eccezioni di routing (EnteNonCensitoException)</li>
 *   <li>Logging transazionale e metriche su successo e errore</li>
 *   <li>Utilizzo dei namespace corretti della PU</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationEndpointTest {

    @Mock
    private PiattaformaUnitariaClient piattaformaClient;

    @Mock
    private ProxyForwardingClient proxyForwardingClient;

    @Mock
    private RoutingDecisionService routingDecisionService;

    @Mock
    private TransactionLoggingService transactionLoggingService;

    @Mock
    private MiddlewareMetricsService metricsService;

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

    /** Decisione di routing verso la PU */
    private static final RoutingDecision DECISION_PU = new RoutingDecision(
            BackendDestinatario.MYPIVOT, ModalitaRouting.PIATTAFORMA_UNITARIA, "http://localhost:8081");

    /** Decisione di routing verso il backend legacy */
    private static final RoutingDecision DECISION_LEGACY = new RoutingDecision(
            BackendDestinatario.MYPIVOT, ModalitaRouting.LEGACY, "http://localhost:8081");

    @BeforeEach
    void setUp() {
        endpoint = new ReconciliationEndpoint(piattaformaClient, proxyForwardingClient,
                routingDecisionService, transactionLoggingService, metricsService);
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

    /**
     * Configura il mock del TransportContext per restituire il path HTTP specificato.
     */
    private MockedStatic<TransportContextHolder> setupTransportContext(String requestPath) {
        MockedStatic<TransportContextHolder> transportMock = mockStatic(TransportContextHolder.class);
        TransportContext transportContext = mock(TransportContext.class);
        HttpServletConnection httpConn = mock(HttpServletConnection.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);

        transportMock.when(TransportContextHolder::getTransportContext).thenReturn(transportContext);
        when(transportContext.getConnection()).thenReturn(httpConn);
        when(httpConn.getHttpServletRequest()).thenReturn(httpRequest);
        when(httpRequest.getRequestURI()).thenReturn(requestPath);

        return transportMock;
    }

    @Nested
    @DisplayName("Routing verso Piattaforma Unitaria")
    class RoutingPU {

        @Test
        @DisplayName("Ente migrato alla PU — inoltra a PiattaformaUnitariaClient")
        void handleRequest_entePU_inoltraAPiattaformaClient() throws Exception {
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

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematiciPagatiRiconciliati")) {

                when(routingDecisionService.decide(
                        eq("SELC_99999000013"), eq("pivotSILAutorizzaImportFlussoTesoreria"), anyString()))
                        .thenReturn(DECISION_PU);

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

                // Verifica che PiattaformaUnitariaClient sia stato chiamato (non ProxyForwardingClient)
                verify(piattaformaClient, times(1)).forwardSoapRequest(anyString(), anyString());
                verifyNoInteractions(proxyForwardingClient);
            }
        }

        @Test
        @DisplayName("Inoltro PU — l'Envelope contiene l'Header con codIpaEnte")
        void handleRequest_entePU_inoltroContieneHeader() throws Exception {
            String puResponseEnvelope =
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                    "<soapenv:Header/>" +
                    "<soapenv:Body>" +
                    "<risposta><esito>OK</esito></risposta>" +
                    "</soapenv:Body>" +
                    "</soapenv:Envelope>";

            setupMessageContextMock(TEST_SOAP_ENVELOPE);

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematici")) {

                when(routingDecisionService.decide(anyString(), anyString(), anyString()))
                        .thenReturn(DECISION_PU);
                when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                        .thenReturn(puResponseEnvelope);

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI, "");

                endpoint.handleReconciliationRequest(requestElement, messageContext);

                var captor = org.mockito.ArgumentCaptor.forClass(String.class);
                verify(piattaformaClient).forwardSoapRequest(anyString(), captor.capture());
                String forwardedEnvelope = captor.getValue();

                assertTrue(forwardedEnvelope.contains("codIpaEnte"),
                        "L'Envelope inoltrato deve contenere codIpaEnte dall'Header SOAP");
                assertTrue(forwardedEnvelope.contains("intestazionePPT"),
                        "L'Envelope inoltrato deve contenere intestazionePPT nell'Header");
            }
        }
    }

    @Nested
    @DisplayName("Routing verso backend legacy")
    class RoutingLegacy {

        @Test
        @DisplayName("Ente legacy — inoltra a ProxyForwardingClient")
        void handleRequest_enteLegacy_inoltraAProxyClient() throws Exception {
            String legacyResponseEnvelope =
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

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematiciPagatiRiconciliati")) {

                when(routingDecisionService.decide(
                        eq("SELC_99999000013"), eq("pivotSILAutorizzaImportFlussoTesoreria"), anyString()))
                        .thenReturn(DECISION_LEGACY);

                when(proxyForwardingClient.forwardToLegacyBackend(
                        eq(BackendDestinatario.MYPIVOT), anyString(), anyString()))
                        .thenReturn(legacyResponseEnvelope);

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI,
                        "<password>BERGAMO</password>");

                Element responseElement = endpoint.handleReconciliationRequest(requestElement, messageContext);

                assertNotNull(responseElement);
                assertEquals("pivotSILAutorizzaImportFlussoTesoreriaRisposta", responseElement.getLocalName());

                // Verifica che ProxyForwardingClient sia stato chiamato (non PiattaformaUnitariaClient)
                verify(proxyForwardingClient, times(1))
                        .forwardToLegacyBackend(eq(BackendDestinatario.MYPIVOT), anyString(), anyString());
                verify(piattaformaClient, never()).forwardSoapRequest(anyString(), anyString());
            }
        }
    }

    @Nested
    @DisplayName("Gestione errori")
    class GestioneErrori {

        @Test
        @DisplayName("EnteNonCensitoException viene propagata (non wrappata in RuntimeException)")
        void handleRequest_enteNonCensito_propagaEccezione() throws Exception {
            setupMessageContextMock(TEST_SOAP_ENVELOPE);

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematici")) {

                when(routingDecisionService.decide(anyString(), anyString(), anyString()))
                        .thenThrow(new EnteNonCensitoException("SELC_99999000013",
                                "pivotSILAutorizzaImportFlussoTesoreria"));

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI, "");

                EnteNonCensitoException ex = assertThrows(EnteNonCensitoException.class,
                        () -> endpoint.handleReconciliationRequest(requestElement, messageContext));

                assertEquals("SELC_99999000013", ex.getCodIpaEnte());
            }
        }

        @Test
        @DisplayName("Errore generico del client — lancia RuntimeException")
        void handleRequest_erroreClient_lanciaRuntimeException() throws Exception {
            setupMessageContextMock(TEST_SOAP_ENVELOPE);

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematici")) {

                when(routingDecisionService.decide(anyString(), anyString(), anyString()))
                        .thenReturn(DECISION_PU);
                when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                        .thenThrow(new RuntimeException("Errore di comunicazione"));

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI, "");

                assertThrows(RuntimeException.class,
                        () -> endpoint.handleReconciliationRequest(requestElement, messageContext));
            }
        }
    }

    @Nested
    @DisplayName("Estrazione codIpaEnte")
    class EstrazioneCodIpaEnte {

        @Test
        @DisplayName("Estrae correttamente codIpaEnte dall'Header SOAP")
        void extractCodIpaEnte_headerValido_estraeCorrettamente() throws Exception {
            String codIpaEnte = endpoint.extractCodIpaEnte(TEST_SOAP_ENVELOPE);
            assertEquals("SELC_99999000013", codIpaEnte);
        }

        @Test
        @DisplayName("codIpaEnte assente → IllegalStateException")
        void extractCodIpaEnte_headerSenzaCodIpa_lanciaEccezione() {
            String envelopeSenzaCodIpa =
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                    "<soapenv:Header><intestazione>vuota</intestazione></soapenv:Header>" +
                    "<soapenv:Body><richiesta/></soapenv:Body>" +
                    "</soapenv:Envelope>";

            assertThrows(IllegalStateException.class,
                    () -> endpoint.extractCodIpaEnte(envelopeSenzaCodIpa));
        }

        @Test
        @DisplayName("codIpaEnte vuoto → IllegalStateException")
        void extractCodIpaEnte_codIpaVuoto_lanciaEccezione() {
            String envelopeCodIpaVuoto =
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                    "<soapenv:Header><intestazionePPT><codIpaEnte></codIpaEnte></intestazionePPT></soapenv:Header>" +
                    "<soapenv:Body><richiesta/></soapenv:Body>" +
                    "</soapenv:Envelope>";

            assertThrows(IllegalStateException.class,
                    () -> endpoint.extractCodIpaEnte(envelopeCodIpaVuoto));
        }
    }

    @Nested
    @DisplayName("Namespace e costanti")
    class NamespaceECostanti {

        @Test
        @DisplayName("NAMESPACE_URI — namespace corretto della PU (veneto)")
        void namespaceUri_isCorrectVenetoNamespace() {
            assertEquals("http://www.regione.veneto.it/pagamenti/pivot/ente/",
                    ReconciliationEndpoint.NAMESPACE_URI);
        }

        @Test
        @DisplayName("HEADER_NAMESPACE_URI — namespace corretto per l'header (ppthead)")
        void headerNamespaceUri_isCorrectPptheadNamespace() {
            assertEquals("http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead",
                    ReconciliationEndpoint.HEADER_NAMESPACE_URI);
        }

        @Test
        @DisplayName("TIPO_OPERAZIONE — corrisponde al local part del messaggio")
        void tipoOperazione_corrispondeAlLocalPart() {
            assertEquals("pivotSILAutorizzaImportFlussoTesoreria",
                    ReconciliationEndpoint.TIPO_OPERAZIONE);
        }
    }

    @Nested
    @DisplayName("Preservazione namespace nelle risposte")
    class PreservazioneNamespace {

        @Test
        @DisplayName("Risposta dalla PU — preserva il namespace veneto")
        void handleRequest_rispostaPU_preservaNamespace() throws Exception {
            String puResponseEnvelope =
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "xmlns:ente=\"http://www.regione.veneto.it/pagamenti/pivot/ente/\">" +
                    "<soapenv:Header/>" +
                    "<soapenv:Body>" +
                    "<ente:risposta><ente:codice>OK</ente:codice></ente:risposta>" +
                    "</soapenv:Body>" +
                    "</soapenv:Envelope>";

            setupMessageContextMock(TEST_SOAP_ENVELOPE);

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematici")) {

                when(routingDecisionService.decide(anyString(), anyString(), anyString()))
                        .thenReturn(DECISION_PU);
                when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                        .thenReturn(puResponseEnvelope);

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI, "");

                Element responseElement = endpoint.handleReconciliationRequest(requestElement, messageContext);

                assertNotNull(responseElement);
                assertEquals("http://www.regione.veneto.it/pagamenti/pivot/ente/",
                        responseElement.getNamespaceURI());
            }
        }
    }

    @Nested
    @DisplayName("Logging transazionale e metriche")
    class LoggingEMetriche {

        @Test
        @DisplayName("Successo — chiama logSuccesso e registraSuccesso")
        void handleRequest_successo_chiamaLoggingEMetriche() throws Exception {
            String puResponseEnvelope =
                    "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                    "<soapenv:Header/>" +
                    "<soapenv:Body><risposta><esito>OK</esito></risposta></soapenv:Body>" +
                    "</soapenv:Envelope>";

            setupMessageContextMock(TEST_SOAP_ENVELOPE);

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematici")) {

                when(routingDecisionService.decide(anyString(), anyString(), anyString()))
                        .thenReturn(DECISION_PU);
                when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                        .thenReturn(puResponseEnvelope);

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI, "");

                endpoint.handleReconciliationRequest(requestElement, messageContext);

                // Verifica che il logging di successo sia stato chiamato
                verify(transactionLoggingService).logSuccesso(
                        eq("SELC_99999000013"),
                        eq("pivotSILAutorizzaImportFlussoTesoreria"),
                        eq(DECISION_PU),
                        eq("/ws/pivot/PagamentiTelematici"),
                        eq(200),
                        anyLong()
                );

                // Verifica che le metriche di successo siano state registrate
                verify(metricsService).registraSuccesso(
                        eq("SELC_99999000013"),
                        eq("pivotSILAutorizzaImportFlussoTesoreria"),
                        eq(DECISION_PU),
                        anyLong()
                );
            }
        }

        @Test
        @DisplayName("Errore post-routing — chiama logErrore e registraErrore con decision")
        void handleRequest_errorePostRouting_chiamaLogErroreConDecision() throws Exception {
            setupMessageContextMock(TEST_SOAP_ENVELOPE);

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematici")) {

                when(routingDecisionService.decide(anyString(), anyString(), anyString()))
                        .thenReturn(DECISION_PU);
                when(piattaformaClient.forwardSoapRequest(anyString(), anyString()))
                        .thenThrow(new RuntimeException("Errore di comunicazione"));

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI, "");

                assertThrows(RuntimeException.class,
                        () -> endpoint.handleReconciliationRequest(requestElement, messageContext));

                // Verifica che il logging di errore sia stato chiamato con decision non-null
                verify(transactionLoggingService).logErrore(
                        eq("SELC_99999000013"),
                        eq("pivotSILAutorizzaImportFlussoTesoreria"),
                        eq(DECISION_PU),
                        eq("/ws/pivot/PagamentiTelematici"),
                        isNull(),
                        eq("Errore di comunicazione"),
                        anyLong()
                );

                // Verifica che le metriche di errore siano state registrate
                verify(metricsService).registraErrore(
                        eq("SELC_99999000013"),
                        eq("pivotSILAutorizzaImportFlussoTesoreria"),
                        eq(DECISION_PU),
                        anyLong()
                );
            }
        }

        @Test
        @DisplayName("Errore pre-routing (ente non censito) — chiama logErrorePreRouting")
        void handleRequest_errorePreRouting_chiamaLogErrorePreRouting() throws Exception {
            setupMessageContextMock(TEST_SOAP_ENVELOPE);

            try (MockedStatic<TransportContextHolder> transportMock =
                         setupTransportContext("/ws/pivot/PagamentiTelematici")) {

                when(routingDecisionService.decide(anyString(), anyString(), anyString()))
                        .thenThrow(new EnteNonCensitoException("SELC_99999000013",
                                "pivotSILAutorizzaImportFlussoTesoreria"));

                Element requestElement = createTestElement(
                        "pivotSILAutorizzaImportFlussoTesoreria",
                        ReconciliationEndpoint.NAMESPACE_URI, "");

                assertThrows(EnteNonCensitoException.class,
                        () -> endpoint.handleReconciliationRequest(requestElement, messageContext));

                // Decision e' null perche' l'eccezione avviene durante decide()
                verify(transactionLoggingService).logErrorePreRouting(
                        eq("SELC_99999000013"),
                        eq("pivotSILAutorizzaImportFlussoTesoreria"),
                        eq("/ws/pivot/PagamentiTelematici"),
                        anyString(),
                        anyLong()
                );

                // Metriche con decision null
                verify(metricsService).registraErrore(
                        eq("SELC_99999000013"),
                        eq("pivotSILAutorizzaImportFlussoTesoreria"),
                        isNull(),
                        anyLong()
                );
            }
        }
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
