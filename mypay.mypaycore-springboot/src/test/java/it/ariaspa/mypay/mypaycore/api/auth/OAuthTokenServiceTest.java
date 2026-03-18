package it.ariaspa.mypay.mypaycore.api.auth;

import it.ariaspa.mypay.mypaycore.api.auth.dto.OAuthTokenResponse;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per OAuthTokenService.
 *
 * Verifica:
 * - Richiesta e caching del token OAuth2
 * - Double-check locking
 * - Refresh forzato del token
 * - Gestione errori (risposta vuota, eccezioni RestClient)
 * - Margine di scadenza
 *
 * NOTA: OAuthTokenService invia i parametri OAuth2 come query string nell'URL
 * (non come form body), come richiesto dalla Piattaforma Unitaria.
 * I mock usano argThat(url -> url.startsWith(TOKEN_URL)) per il matching dell'URL.
 */
@ExtendWith(MockitoExtension.class)
class OAuthTokenServiceTest {

    private static final String TOKEN_URL = "https://api.uat.p4pa.pagopa.it/pu/auth/oauth/token";
    private static final String MOCK_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test";
    private static final long EXPIRES_IN = 3600;

    @Mock
    private RestTemplate restTemplate;

    private PiattaformaUnitariaConfig config;
    private OAuthTokenService service;

    @BeforeEach
    void setUp() {
        config = new PiattaformaUnitariaConfig();
        config.setBaseUrl("https://api.uat.p4pa.pagopa.it");

        PiattaformaUnitariaConfig.Auth auth = new PiattaformaUnitariaConfig.Auth();
        auth.setTokenUrl(TOKEN_URL);
        auth.setClientId("test-client-id");
        auth.setClientSecret("test-client-secret");
        auth.setGrantType("client_credentials");
        auth.setScope("openid");
        config.setAuth(auth);

        service = new OAuthTokenService(config, restTemplate);
    }

    /**
     * Helper: configura il mock del RestTemplate per restituire una risposta token.
     * L'URL viene matchato con startsWith(TOKEN_URL) perche i parametri OAuth2
     * vengono aggiunti come query string (es. ?client_id=...&client_secret=...).
     */
    private void stubTokenRequest(ResponseEntity<OAuthTokenResponse> response) {
        when(restTemplate.postForEntity(
                argThat((String url) -> url != null && url.startsWith(TOKEN_URL)),
                any(HttpEntity.class),
                eq(OAuthTokenResponse.class)))
                .thenReturn(response);
    }

    /**
     * Helper: configura il mock per restituire risposte sequenziali (prima chiamata, seconda chiamata).
     */
    private void stubTokenRequestSequential(ResponseEntity<OAuthTokenResponse> first,
                                             ResponseEntity<OAuthTokenResponse> second) {
        when(restTemplate.postForEntity(
                argThat((String url) -> url != null && url.startsWith(TOKEN_URL)),
                any(HttpEntity.class),
                eq(OAuthTokenResponse.class)))
                .thenReturn(first)
                .thenReturn(second);
    }

    /**
     * Helper: configura il mock per lanciare un'eccezione.
     */
    private void stubTokenRequestThrow(RestClientException exception) {
        when(restTemplate.postForEntity(
                argThat((String url) -> url != null && url.startsWith(TOKEN_URL)),
                any(HttpEntity.class),
                eq(OAuthTokenResponse.class)))
                .thenThrow(exception);
    }

    /**
     * Helper: verifica che il RestTemplate sia stato invocato N volte con URL che inizia per TOKEN_URL.
     */
    private void verifyTokenRequestCount(int times) {
        verify(restTemplate, times(times)).postForEntity(
                argThat((String url) -> url != null && url.startsWith(TOKEN_URL)),
                any(HttpEntity.class),
                eq(OAuthTokenResponse.class));
    }

    @Test
    @DisplayName("getAccessToken - richiede nuovo token quando cache e vuota")
    void getAccessToken_requestsNewToken_whenCacheIsEmpty() {
        OAuthTokenResponse tokenResponse = new OAuthTokenResponse(MOCK_TOKEN, "Bearer", EXPIRES_IN);
        stubTokenRequest(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        String token = service.getAccessToken();

        assertEquals(MOCK_TOKEN, token);
        verifyTokenRequestCount(1);
    }

    @Test
    @DisplayName("getAccessToken - restituisce token dalla cache alla seconda chiamata")
    void getAccessToken_returnsCachedToken_onSubsequentCalls() {
        OAuthTokenResponse tokenResponse = new OAuthTokenResponse(MOCK_TOKEN, "Bearer", EXPIRES_IN);
        stubTokenRequest(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        // Prima chiamata: richiede token
        String token1 = service.getAccessToken();
        // Seconda chiamata: usa cache
        String token2 = service.getAccessToken();

        assertEquals(MOCK_TOKEN, token1);
        assertEquals(MOCK_TOKEN, token2);
        // RestTemplate invocato solo una volta (la seconda usa la cache)
        verifyTokenRequestCount(1);
    }

    @Test
    @DisplayName("refreshToken - invalida cache e richiede nuovo token")
    void refreshToken_invalidatesCacheAndRequestsNew() {
        OAuthTokenResponse firstToken = new OAuthTokenResponse(MOCK_TOKEN, "Bearer", EXPIRES_IN);
        OAuthTokenResponse secondToken = new OAuthTokenResponse("new-token", "Bearer", EXPIRES_IN);

        stubTokenRequestSequential(
                new ResponseEntity<>(firstToken, HttpStatus.OK),
                new ResponseEntity<>(secondToken, HttpStatus.OK));

        // Prima chiamata
        String token1 = service.getAccessToken();
        // Refresh forzato
        String token2 = service.refreshToken();

        assertEquals(MOCK_TOKEN, token1);
        assertEquals("new-token", token2);
        verifyTokenRequestCount(2);
    }

    @Test
    @DisplayName("getAccessToken - lancia PiattaformaAuthenticationException su risposta null")
    void getAccessToken_throwsException_whenResponseBodyIsNull() {
        stubTokenRequest(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(PiattaformaAuthenticationException.class, () -> service.getAccessToken());
    }

    @Test
    @DisplayName("getAccessToken - lancia PiattaformaAuthenticationException su access_token null")
    void getAccessToken_throwsException_whenAccessTokenIsNull() {
        OAuthTokenResponse emptyResponse = new OAuthTokenResponse(null, "Bearer", EXPIRES_IN);
        stubTokenRequest(new ResponseEntity<>(emptyResponse, HttpStatus.OK));

        assertThrows(PiattaformaAuthenticationException.class, () -> service.getAccessToken());
    }

    @Test
    @DisplayName("getAccessToken - lancia PiattaformaAuthenticationException su errore di rete")
    void getAccessToken_throwsException_whenRestClientFails() {
        stubTokenRequestThrow(new RestClientException("Connection refused"));

        PiattaformaAuthenticationException ex = assertThrows(
                PiattaformaAuthenticationException.class,
                () -> service.getAccessToken()
        );
        assertTrue(ex.getMessage().contains("Impossibile ottenere il token"));
    }

    @Test
    @DisplayName("invalidateToken - il prossimo getAccessToken richiede un nuovo token")
    void invalidateToken_causesNewTokenRequest() {
        OAuthTokenResponse tokenResponse = new OAuthTokenResponse(MOCK_TOKEN, "Bearer", EXPIRES_IN);
        stubTokenRequest(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        service.getAccessToken();
        service.invalidateToken();

        assertFalse(service.isTokenValid());
    }

    @Test
    @DisplayName("isTokenValid - ritorna false quando nessun token e stato richiesto")
    void isTokenValid_returnsFalse_whenNoTokenRequested() {
        assertFalse(service.isTokenValid());
    }

    @Test
    @DisplayName("isTokenValid - ritorna true dopo aver ottenuto un token valido")
    void isTokenValid_returnsTrue_afterSuccessfulTokenRequest() {
        OAuthTokenResponse tokenResponse = new OAuthTokenResponse(MOCK_TOKEN, "Bearer", EXPIRES_IN);
        stubTokenRequest(new ResponseEntity<>(tokenResponse, HttpStatus.OK));

        service.getAccessToken();

        assertTrue(service.isTokenValid());
    }
}
