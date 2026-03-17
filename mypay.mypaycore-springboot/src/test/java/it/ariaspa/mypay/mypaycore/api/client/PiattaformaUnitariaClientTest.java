package it.ariaspa.mypay.mypaycore.api.client;

import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenInterceptor;
import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenService;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import it.ariaspa.mypay.mypaycore.api.config.PiattaformaUnitariaConfig;
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
 * Test unitari per PiattaformaUnitariaClient.
 *
 * Verifica:
 * - Inoltro corretto delle richieste SOAP
 * - Retry automatico su 401 Unauthorized con refresh token
 * - Gestione degli errori HTTP (4xx, 5xx)
 * - Gestione dei timeout e errori di rete
 * - Fallback del circuit breaker
 */
@ExtendWith(MockitoExtension.class)
class PiattaformaUnitariaClientTest {

    private static final String BASE_URL = "https://api.uat.p4pa.pagopa.it";
    private static final String PATH = "/pu/sil/soap/reconciliation/test";
    private static final String SOAP_REQUEST = "<request>test</request>";
    private static final String SOAP_RESPONSE = "<response>ok</response>";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OAuthTokenInterceptor oAuthTokenInterceptor;

    @Mock
    private OAuthTokenService oAuthTokenService;

    private PiattaformaUnitariaConfig config;
    private PiattaformaUnitariaClient client;

    @BeforeEach
    void setUp() {
        config = new PiattaformaUnitariaConfig();
        config.setBaseUrl(BASE_URL);

        client = new PiattaformaUnitariaClient(config, oAuthTokenInterceptor, oAuthTokenService, restTemplate);
        client.init();
    }

    @Test
    @DisplayName("forwardSoapRequest - inoltra richiesta e restituisce risposta")
    void forwardSoapRequest_success() {
        when(restTemplate.postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(SOAP_RESPONSE, HttpStatus.OK));

        String response = client.forwardSoapRequest(PATH, SOAP_REQUEST);

        assertEquals(SOAP_RESPONSE, response);
        verify(restTemplate, times(1)).postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("forwardSoapRequest - retry con nuovo token su 401 Unauthorized")
    void forwardSoapRequest_retryOn401() {
        when(restTemplate.postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null))
                .thenReturn(new ResponseEntity<>(SOAP_RESPONSE, HttpStatus.OK));
        when(oAuthTokenService.refreshToken()).thenReturn("new-token");

        String response = client.forwardSoapRequest(PATH, SOAP_REQUEST);

        assertEquals(SOAP_RESPONSE, response);
        verify(oAuthTokenService, times(1)).refreshToken();
        verify(restTemplate, times(2)).postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("forwardSoapRequest - lancia PiattaformaAuthenticationException se retry 401 fallisce")
    void forwardSoapRequest_throwsAuthException_whenRetryAlsoFails401() {
        when(restTemplate.postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));
        when(oAuthTokenService.refreshToken()).thenReturn("new-token");

        assertThrows(PiattaformaAuthenticationException.class,
                () -> client.forwardSoapRequest(PATH, SOAP_REQUEST));
    }

    @Test
    @DisplayName("forwardSoapRequest - lancia PiattaformaCommunicationException su errore HTTP 500")
    void forwardSoapRequest_throwsCommunicationException_onServerError() {
        when(restTemplate.postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null));

        PiattaformaCommunicationException ex = assertThrows(
                PiattaformaCommunicationException.class,
                () -> client.forwardSoapRequest(PATH, SOAP_REQUEST)
        );
        assertEquals(500, ex.getHttpStatus());
    }

    @Test
    @DisplayName("forwardSoapRequest - lancia PiattaformaCommunicationException su errore HTTP 400")
    void forwardSoapRequest_throwsCommunicationException_onBadRequest() {
        when(restTemplate.postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        PiattaformaCommunicationException ex = assertThrows(
                PiattaformaCommunicationException.class,
                () -> client.forwardSoapRequest(PATH, SOAP_REQUEST)
        );
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    @DisplayName("forwardSoapRequest - lancia PiattaformaCommunicationException su timeout")
    void forwardSoapRequest_throwsCommunicationException_onTimeout() {
        when(restTemplate.postForEntity(eq(BASE_URL + PATH), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        PiattaformaCommunicationException ex = assertThrows(
                PiattaformaCommunicationException.class,
                () -> client.forwardSoapRequest(PATH, SOAP_REQUEST)
        );
        assertTrue(ex.getMessage().contains("Timeout"));
    }

    @Test
    @DisplayName("forwardSoapRequestFallback - lancia PiattaformaCommunicationException con messaggio circuit breaker")
    void forwardSoapRequestFallback_throwsCommunicationException() {
        PiattaformaCommunicationException cause = new PiattaformaCommunicationException("test", 503);

        PiattaformaCommunicationException ex = assertThrows(
                PiattaformaCommunicationException.class,
                () -> client.forwardSoapRequestFallback(PATH, SOAP_REQUEST, cause)
        );
        assertTrue(ex.getMessage().contains("circuit breaker"));
        assertEquals(503, ex.getHttpStatus());
    }
}
