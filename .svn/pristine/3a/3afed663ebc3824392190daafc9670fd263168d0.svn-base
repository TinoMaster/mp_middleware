package it.ariaspa.mypay.mypaycore.api.auth;

import it.ariaspa.mypay.mypaycore.api.auth.dto.OAuthTokenResponse;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.config.PiattaformaUnitariaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servizio responsabile della gestione dell'autenticazione OAuth2
 * verso la Piattaforma Unitaria, con supporto multi-ente.
 *
 * <p>Ogni ente ha le proprie credenziali {@code client_id} e {@code client_secret}
 * memorizzate nella tabella {@code mygov_ente_config_pu}. I parametri globali
 * ({@code token_url}, {@code grant_type}, {@code scope}) restano in
 * {@code application.properties}.
 *
 * <p>Funzionalita:
 * <ul>
 *   <li>Richiesta token tramite Client Credentials Flow per ogni ente</li>
 *   <li>Cache in-memory per-ente ({@code ConcurrentHashMap<codIpaEnte, TokenData>})</li>
 *   <li>Refresh automatico del token scaduto</li>
 *   <li>Thread-safe tramite {@link ReentrantLock} per-ente</li>
 *   <li>Timeout configurabili per le richieste HTTP</li>
 * </ul>
 *
 * <p>Il token per ogni ente viene richiesto al primo utilizzo e mantenuto in cache
 * fino alla scadenza (con un margine di sicurezza di {@value #TOKEN_EXPIRY_MARGIN_SECONDS} secondi).
 */
@Service
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    /**
     * Margine di sicurezza in secondi prima della scadenza effettiva del token.
     * Il token viene considerato scaduto 60 secondi prima della scadenza reale
     * per evitare di usare un token che potrebbe scadere durante una richiesta.
     */
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 60;

    /** Timeout di connessione per le richieste OAuth2 (millisecondi). */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Timeout di lettura per le richieste OAuth2 (millisecondi). */
    private static final int READ_TIMEOUT_MS = 10_000;

    /** Configurazione globale della Piattaforma Unitaria (token_url, scope, grant_type). */
    private final PiattaformaUnitariaConfig config;

    /** RestTemplate per le richieste HTTP all'endpoint OAuth2. */
    private final RestTemplate restTemplate;

    /**
     * Cache dei token per-ente: {@code codIpaEnte -> TokenData}.
     * Thread-safe per letture concorrenti; i lock per-ente gestiscono i refresh.
     */
    private final ConcurrentHashMap<String, TokenData> tokenCache = new ConcurrentHashMap<>();

    /**
     * Lock per-ente: evita che piu' thread richiedano contemporaneamente un token
     * per lo stesso ente. Creato lazily e mantenuto per tutta la vita del servizio.
     */
    private final ConcurrentHashMap<String, ReentrantLock> lockPerEnte = new ConcurrentHashMap<>();

    @Autowired
    public OAuthTokenService(PiattaformaUnitariaConfig config) {
        this.config = config;
        this.restTemplate = createRestTemplateWithTimeouts();
    }

    /**
     * Restituisce un token OAuth2 valido per l'ente specificato.
     *
     * <p>Se il token in cache e' valido, lo restituisce direttamente.
     * Se il token e' scaduto o assente, ne richiede uno nuovo usando le
     * credenziali ({@code clientId}, {@code clientSecret}) specifiche dell'ente.
     *
     * @param codIpaEnte   codice IPA dell'ente per cui richiedere il token
     * @param clientId     client ID OAuth2 specifico dell'ente
     * @param clientSecret client secret OAuth2 specifico dell'ente
     * @return token Bearer valido
     * @throws PiattaformaAuthenticationException se non e' possibile ottenere il token
     */
    public String getAccessToken(String codIpaEnte, String clientId, String clientSecret) {
        TokenData tokenData = tokenCache.get(codIpaEnte);
        if (tokenData != null && tokenData.isValid()) {
            log.debug("Utilizzo token OAuth2 dalla cache per ente '{}' (scadenza: {})",
                    codIpaEnte, tokenData.expiryTime);
            return tokenData.token;
        }

        // Acquisisce il lock per-ente per evitare richieste token duplicate
        ReentrantLock lock = lockPerEnte.computeIfAbsent(codIpaEnte, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check dopo aver acquisito il lock
            TokenData doubleCheck = tokenCache.get(codIpaEnte);
            if (doubleCheck != null && doubleCheck.isValid()) {
                return doubleCheck.token;
            }

            log.info("Richiesta nuovo token OAuth2 per ente '{}' alla Piattaforma Unitaria", codIpaEnte);
            return requestNewToken(codIpaEnte, clientId, clientSecret);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forza il refresh del token per un ente, invalidando quello in cache.
     * Utile in caso di risposta 401 dalla piattaforma.
     *
     * @param codIpaEnte   codice IPA dell'ente
     * @param clientId     client ID OAuth2 dell'ente
     * @param clientSecret client secret OAuth2 dell'ente
     * @return nuovo token Bearer
     * @throws PiattaformaAuthenticationException se non e' possibile ottenere il token
     */
    public String refreshToken(String codIpaEnte, String clientId, String clientSecret) {
        ReentrantLock lock = lockPerEnte.computeIfAbsent(codIpaEnte, k -> new ReentrantLock());
        lock.lock();
        try {
            log.info("Refresh forzato del token OAuth2 per ente '{}'", codIpaEnte);
            invalidateToken(codIpaEnte);
            return requestNewToken(codIpaEnte, clientId, clientSecret);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Invalida il token in cache per l'ente specificato.
     *
     * @param codIpaEnte codice IPA dell'ente di cui invalidare il token
     */
    public void invalidateToken(String codIpaEnte) {
        tokenCache.remove(codIpaEnte);
        log.debug("Token OAuth2 invalidato per ente '{}'", codIpaEnte);
    }

    /**
     * Verifica se il token in cache per l'ente specificato e' ancora valido.
     *
     * @param codIpaEnte codice IPA dell'ente da verificare
     * @return {@code true} se il token esiste e non e' scaduto
     */
    public boolean isTokenValid(String codIpaEnte) {
        TokenData tokenData = tokenCache.get(codIpaEnte);
        return tokenData != null && tokenData.isValid();
    }

    /**
     * Restituisce il numero di token in cache (uno per ente).
     *
     * @return numero di enti con token in cache
     */
    public int getTokenCacheSize() {
        return tokenCache.size();
    }

    /**
     * Restituisce una copia delle chiavi (codici IPA) presenti nella cache dei token.
     * Utile per ispezione e health check.
     *
     * @return insieme dei codici IPA degli enti con token in cache
     */
    public java.util.Set<String> getEntiInCache() {
        return java.util.Collections.unmodifiableSet(tokenCache.keySet());
    }

    /**
     * Effettua la richiesta HTTP POST all'endpoint OAuth2 per ottenere un nuovo token.
     * I parametri vengono inviati come query string nell'URL (richiesto dalla Piattaforma Unitaria).
     *
     * @param codIpaEnte   codice IPA dell'ente (per il logging)
     * @param clientId     client ID OAuth2 dell'ente
     * @param clientSecret client secret OAuth2 dell'ente
     * @return token Bearer ottenuto
     * @throws PiattaformaAuthenticationException in caso di errore nella richiesta
     */
    private String requestNewToken(String codIpaEnte, String clientId, String clientSecret) {
        PiattaformaUnitariaConfig.Auth authConfig = config.getAuth();

        // La Piattaforma Unitaria richiede i parametri come query string nell'URL
        String tokenUrlWithParams = authConfig.getTokenUrl()
                + "?client_id=" + clientId
                + "&client_secret=" + clientSecret
                + "&grant_type=" + authConfig.getGrantType()
                + "&scope=" + authConfig.getScope();

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            log.debug("Invio richiesta token OAuth2 per ente '{}' a: {}",
                    codIpaEnte, authConfig.getTokenUrl());

            ResponseEntity<OAuthTokenResponse> response = restTemplate.postForEntity(
                    tokenUrlWithParams,
                    request,
                    OAuthTokenResponse.class
            );

            if (response.getBody() == null || response.getBody().getAccessToken() == null) {
                throw new PiattaformaAuthenticationException(
                        "Risposta OAuth2 vuota o senza access_token per ente '" + codIpaEnte + "'");
            }

            OAuthTokenResponse tokenResponse = response.getBody();
            Instant expiry = Instant.now()
                    .plusSeconds(tokenResponse.getExpiresIn())
                    .minusSeconds(TOKEN_EXPIRY_MARGIN_SECONDS);

            tokenCache.put(codIpaEnte, new TokenData(tokenResponse.getAccessToken(), expiry));

            log.info("Token OAuth2 ottenuto per ente '{}'. Scadenza: {}, Tipo: {}",
                    codIpaEnte, expiry, tokenResponse.getTokenType());

            return tokenResponse.getAccessToken();

        } catch (RestClientException e) {
            log.error("Errore nella richiesta del token OAuth2 per ente '{}': {}",
                    codIpaEnte, e.getMessage(), e);
            throw new PiattaformaAuthenticationException(
                    "Impossibile ottenere il token OAuth2 per ente '" + codIpaEnte + "'", e);
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

    /**
     * Contenitore immutabile per i dati del token OAuth2 di un singolo ente.
     */
    private static final class TokenData {

        /** Valore del token Bearer. */
        final String token;

        /** Istante di scadenza del token (gia' con margine di sicurezza applicato). */
        final Instant expiryTime;

        /**
         * Crea un nuovo TokenData.
         *
         * @param token      il token Bearer
         * @param expiryTime l'istante di scadenza (con margine gia' sottratto)
         */
        TokenData(String token, Instant expiryTime) {
            this.token = token;
            this.expiryTime = expiryTime;
        }

        /**
         * Verifica se il token e' ancora valido (non scaduto).
         *
         * @return {@code true} se il token non e' scaduto
         */
        boolean isValid() {
            return Instant.now().isBefore(expiryTime);
        }
    }
}
