package it.ariaspa.mypay.mypaycore.api.auth;

import it.ariaspa.mypay.mypaycore.api.auth.dto.OAuthTokenResponse;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.config.PiattaformaUnitariaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servizio responsabile della gestione dell'autenticazione OAuth2
 * verso la Piattaforma Unitaria.
 *
 * Funzionalita:
 * - Richiesta token tramite Client Credentials Flow
 * - Caching in-memory del token con verifica scadenza
 * - Refresh automatico del token scaduto
 * - Thread-safe tramite ReentrantLock
 * - Timeout configurabili per le richieste HTTP
 *
 * Il token viene richiesto al primo utilizzo e mantenuto in cache
 * fino alla scadenza (con un margine di sicurezza di 60 secondi).
 */
@Service
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    /**
     * Margine di sicurezza in secondi prima della scadenza effettiva del token.
     * Il token viene considerato scaduto 60 secondi prima della scadenza reale
     * per evitare di utilizzare un token che potrebbe scadere durante una richiesta.
     */
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 60;

    /** Timeout di connessione per le richieste OAuth2 (millisecondi). */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Timeout di lettura per le richieste OAuth2 (millisecondi). */
    private static final int READ_TIMEOUT_MS = 10_000;

    private final PiattaformaUnitariaConfig config;
    private final RestTemplate restTemplate;
    private final ReentrantLock tokenLock = new ReentrantLock();

    /** Token corrente in cache. */
    private volatile String cachedToken;

    /** Istante di scadenza del token corrente. */
    private volatile Instant tokenExpiryTime;

    @Autowired
    public OAuthTokenService(PiattaformaUnitariaConfig config) {
        this.config = config;
        this.restTemplate = createRestTemplateWithTimeouts();
    }

    /**
     * Costruttore per testing che consente di iniettare un RestTemplate mock.
     *
     * @param config       la configurazione della piattaforma
     * @param restTemplate il RestTemplate da utilizzare (tipicamente un mock)
     */
    OAuthTokenService(PiattaformaUnitariaConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    /**
     * Restituisce un token di accesso valido per la Piattaforma Unitaria.
     *
     * Se il token in cache e valido, lo restituisce direttamente.
     * Se il token e scaduto o assente, ne richiede uno nuovo.
     *
     * @return token di accesso JWT valido
     * @throws PiattaformaAuthenticationException se non e possibile ottenere il token
     */
    public String getAccessToken() {
        if (isTokenValid()) {
            log.debug("Utilizzo token OAuth2 dalla cache (scadenza: {})", tokenExpiryTime);
            return cachedToken;
        }

        tokenLock.lock();
        try {
            // Double-check dopo aver acquisito il lock
            if (isTokenValid()) {
                return cachedToken;
            }

            log.info("Richiesta nuovo token OAuth2 alla Piattaforma Unitaria");
            return requestNewToken();
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Forza il refresh del token, invalidando quello in cache.
     * Utile in caso di errore 401 dalla piattaforma.
     *
     * @return nuovo token di accesso
     * @throws PiattaformaAuthenticationException se non e possibile ottenere il token
     */
    public String refreshToken() {
        tokenLock.lock();
        try {
            log.info("Refresh forzato del token OAuth2");
            invalidateToken();
            return requestNewToken();
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Invalida il token corrente in cache.
     */
    public void invalidateToken() {
        this.cachedToken = null;
        this.tokenExpiryTime = null;
        log.debug("Token OAuth2 invalidato");
    }

    /**
     * Verifica se il token corrente e ancora valido.
     *
     * @return true se il token esiste e non e scaduto (considerando il margine di sicurezza)
     */
    public boolean isTokenValid() {
        return cachedToken != null
                && tokenExpiryTime != null
                && Instant.now().isBefore(tokenExpiryTime);
    }

    /**
     * Effettua la richiesta HTTP POST all'endpoint OAuth2 per ottenere un nuovo token.
     * I parametri vengono inviati come application/x-www-form-urlencoded.
     *
     * @return token di accesso ottenuto
     * @throws PiattaformaAuthenticationException in caso di errore nella richiesta
     */
    private String requestNewToken() {
        PiattaformaUnitariaConfig.Auth authConfig = config.getAuth();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", authConfig.getClientId());
        params.add("client_secret", authConfig.getClientSecret());
        params.add("grant_type", authConfig.getGrantType());
        params.add("scope", authConfig.getScope());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            log.debug("Invio richiesta token OAuth2 a: {}", authConfig.getTokenUrl());

            ResponseEntity<OAuthTokenResponse> response = restTemplate.postForEntity(
                    authConfig.getTokenUrl(),
                    request,
                    OAuthTokenResponse.class
            );

            if (response.getBody() == null || response.getBody().getAccessToken() == null) {
                throw new PiattaformaAuthenticationException(
                        "Risposta OAuth2 vuota o senza access_token dalla Piattaforma Unitaria");
            }

            OAuthTokenResponse tokenResponse = response.getBody();
            this.cachedToken = tokenResponse.getAccessToken();
            this.tokenExpiryTime = Instant.now()
                    .plusSeconds(tokenResponse.getExpiresIn())
                    .minusSeconds(TOKEN_EXPIRY_MARGIN_SECONDS);

            log.info("Token OAuth2 ottenuto con successo. Scadenza: {}, Tipo: {}",
                    tokenExpiryTime, tokenResponse.getTokenType());

            return cachedToken;

        } catch (RestClientException e) {
            log.error("Errore nella richiesta del token OAuth2 alla Piattaforma Unitaria: {}",
                    e.getMessage(), e);
            throw new PiattaformaAuthenticationException(
                    "Impossibile ottenere il token OAuth2 dalla Piattaforma Unitaria", e);
        }
    }

    /**
     * Crea un RestTemplate con timeout di connessione e lettura configurati.
     */
    private static RestTemplate createRestTemplateWithTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
