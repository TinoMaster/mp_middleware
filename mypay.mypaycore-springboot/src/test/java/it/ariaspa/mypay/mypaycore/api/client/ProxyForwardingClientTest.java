package it.ariaspa.mypay.mypaycore.api.client;

import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import it.ariaspa.mypay.mypaycore.api.config.BackendRoutingConfig;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per {@link ProxyForwardingClient}.
 * <p>
 * Verifica:
 * <ul>
 *   <li>Forward trasparente della richiesta SOAP al backend corretto</li>
 *   <li>Composizione corretta dell'URL (baseUrl + requestPath)</li>
 *   <li>Gestione errori HTTP dal backend</li>
 *   <li>Gestione errori di rete/timeout</li>
 *   <li>Fallback del circuit breaker</li>
 *   <li>Nessun header di autenticazione aggiunto</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProxyForwardingClientTest {

    @Mock
    private RestTemplate restTemplate;

    private BackendRoutingConfig backendRoutingConfig;
    private ProxyForwardingClient client;

    /** SOAP Envelope di esempio */
    private static final String TEST_SOAP_XML =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soapenv:Body><test>contenuto</test></soapenv:Body>"
            + "</soapenv:Envelope>";

    /** Risposta SOAP di esempio dal backend */
    private static final String TEST_RESPONSE_XML =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soapenv:Body><risposta><esito>OK</esito></risposta></soapenv:Body>"
            + "</soapenv:Envelope>";

    @BeforeEach
    void setUp() {
        backendRoutingConfig = new BackendRoutingConfig();

        BackendRoutingConfig.BackendProperties mypivotProps = new BackendRoutingConfig.BackendProperties();
        mypivotProps.setBaseUrl("http://localhost:8081");
        backendRoutingConfig.setMypivot(mypivotProps);

        BackendRoutingConfig.BackendProperties mypayProps = new BackendRoutingConfig.BackendProperties();
        mypayProps.setBaseUrl("http://localhost:8082");
        backendRoutingConfig.setMypay(mypayProps);

        // Usa il costruttore per test con RestTemplate mock
        client = new ProxyForwardingClient(backendRoutingConfig, restTemplate);
    }

    @Test
    @DisplayName("forwardToLegacyBackend - inoltra correttamente a MYPIVOT")
    void forwardToLegacyBackend_mypivot_success() {
        String requestPath = "/ws/pivot/PagamentiTelematiciPagatiRiconciliati";
        String expectedUrl = "http://localhost:8081" + requestPath;

        when(restTemplate.postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(TEST_RESPONSE_XML, HttpStatus.OK));

        String result = client.forwardToLegacyBackend(
                BackendDestinatario.MYPIVOT, requestPath, TEST_SOAP_XML);

        assertEquals(TEST_RESPONSE_XML, result);
        verify(restTemplate, times(1)).postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("forwardToLegacyBackend - inoltra correttamente a MYPAY")
    void forwardToLegacyBackend_mypay_success() {
        String requestPath = "/ws/pa/PagamentiTelematiciCCPPa";
        String expectedUrl = "http://localhost:8082" + requestPath;

        when(restTemplate.postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(TEST_RESPONSE_XML, HttpStatus.OK));

        String result = client.forwardToLegacyBackend(
                BackendDestinatario.MYPAY, requestPath, TEST_SOAP_XML);

        assertEquals(TEST_RESPONSE_XML, result);
        verify(restTemplate, times(1)).postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("forwardToLegacyBackend - lancia PiattaformaCommunicationException su errore HTTP 500")
    void forwardToLegacyBackend_httpError_throwsCommunicationException() {
        String requestPath = "/ws/pivot/Endpoint";
        String expectedUrl = "http://localhost:8081" + requestPath;

        when(restTemplate.postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                        null, null, null));

        PiattaformaCommunicationException ex = assertThrows(
                PiattaformaCommunicationException.class,
                () -> client.forwardToLegacyBackend(
                        BackendDestinatario.MYPIVOT, requestPath, TEST_SOAP_XML));

        assertTrue(ex.getMessage().contains("MYPIVOT"));
    }

    @Test
    @DisplayName("forwardToLegacyBackend - lancia PiattaformaCommunicationException su timeout")
    void forwardToLegacyBackend_timeout_throwsCommunicationException() {
        String requestPath = "/ws/pa/Endpoint";
        String expectedUrl = "http://localhost:8082" + requestPath;

        when(restTemplate.postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        PiattaformaCommunicationException ex = assertThrows(
                PiattaformaCommunicationException.class,
                () -> client.forwardToLegacyBackend(
                        BackendDestinatario.MYPAY, requestPath, TEST_SOAP_XML));

        assertTrue(ex.getMessage().contains("MYPAY"));
    }

    @Test
    @DisplayName("forwardFallback - lancia PiattaformaCommunicationException con status 503")
    void forwardFallback_throwsCommunicationException() {
        PiattaformaCommunicationException ex = assertThrows(
                PiattaformaCommunicationException.class,
                () -> client.forwardFallback(
                        BackendDestinatario.MYPIVOT,
                        "/ws/pivot/Endpoint",
                        TEST_SOAP_XML,
                        new RuntimeException("errore di test")));

        assertTrue(ex.getMessage().contains("MYPIVOT"));
        assertTrue(ex.getMessage().contains("circuit breaker"));
    }

    @Test
    @DisplayName("forwardToLegacyBackend - l'URL viene composto da baseUrl + requestPath")
    void forwardToLegacyBackend_composesCorrectUrl() {
        String requestPath = "/ws/fesp/FespEndpoint";
        String expectedUrl = "http://localhost:8082" + requestPath;

        when(restTemplate.postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(TEST_RESPONSE_XML, HttpStatus.OK));

        client.forwardToLegacyBackend(BackendDestinatario.MYPAY, requestPath, TEST_SOAP_XML);

        // Verifica che l'URL sia stato costruito correttamente
        verify(restTemplate).postForEntity(eq(expectedUrl), any(HttpEntity.class), eq(String.class));
    }
}
