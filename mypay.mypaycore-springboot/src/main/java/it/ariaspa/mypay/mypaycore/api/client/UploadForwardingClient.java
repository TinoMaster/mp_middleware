package it.ariaspa.mypay.mypaycore.api.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenService;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import it.ariaspa.mypay.mypaycore.api.domain.EnteCompleto;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Client HTTP per l'inoltro dei file di upload verso i backend (legacy e PU).
 *
 * <p>Gestisce l'inoltro multipart del file caricato dal SIL verso l'endpoint
 * di upload del backend corretto. Supporta due modalità:
 * <ul>
 *   <li><strong>Legacy</strong>: POST multipart senza OAuth2
 *       (l'authorizationToken nel body è sufficiente)</li>
 *   <li><strong>PU</strong>: POST multipart con header OAuth2 Bearer
 *       + authorizationToken nel body</li>
 * </ul>
 *
 * <p>I timeout sono configurabili e più elevati rispetto ai client SOAP,
 * dato che i file possono essere di dimensioni significative.
 *
 * @see it.ariaspa.mypay.mypaycore.api.upload.UploadFlussoController
 */
@Service
public class UploadForwardingClient {

    private static final Logger log = LoggerFactory.getLogger(UploadForwardingClient.class);

    private final OAuthTokenService oAuthTokenService;
    private final EnteCacheService enteCacheService;

    /** Timeout di connessione (millisecondi). */
    private final int connectTimeoutMs;

    /** Timeout di lettura (millisecondi). */
    private final int readTimeoutMs;

    private RestTemplate restTemplate;

    public UploadForwardingClient(
            OAuthTokenService oAuthTokenService,
            EnteCacheService enteCacheService,
            @Value("${middleware.upload.proxy.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${middleware.upload.proxy.read-timeout-ms:120000}") int readTimeoutMs) {
        this.oAuthTokenService = oAuthTokenService;
        this.enteCacheService = enteCacheService;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Inizializza il RestTemplate con i timeout configurati per upload di file grandi.
     */
    @PostConstruct
    public void init() {
        if (this.restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeoutMs);
            factory.setReadTimeout(readTimeoutMs);
            // Disabilita il buffering per evitare di caricare file grandi in memoria
            factory.setBufferRequestBody(false);
            this.restTemplate = new RestTemplate(factory);
        }
        log.info("UploadForwardingClient inizializzato. connectTimeout: {}ms, readTimeout: {}ms",
                connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Inoltra il file di upload al backend legacy (senza OAuth2).
     *
     * <p>Costruisce una richiesta multipart identica a quella che il SIL
     * farebbe direttamente verso {@code MyBoxController.uploadByWS} del backend legacy.
     *
     * @param uploadUrl          URL di upload originale del backend legacy
     * @param authorizationToken token JWT di autorizzazione generato dal backend
     * @param file               il file caricato dal SIL
     * @return la risposta del backend come stringa (JSON)
     * @throws PiattaformaCommunicationException in caso di errore di comunicazione
     */
    @CircuitBreaker(name = "backendLegacy", fallbackMethod = "forwardUploadFallback")
    @Retry(name = "backendLegacy")
    public String inoltraAlLegacy(String uploadUrl, String authorizationToken,
                                  MultipartFile file) {
        log.info("Inoltro file upload al backend legacy: {} (file: '{}', dimensione: {} byte)",
                uploadUrl, file.getOriginalFilename(), file.getSize());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = costruisciBodyMultipart(authorizationToken, file);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        return eseguiRichiestaUpload(uploadUrl, request, "legacy");
    }

    /**
     * Inoltra il file di upload alla Piattaforma Unitaria (con OAuth2).
     *
     * <p>Aggiunge l'header {@code Authorization: Bearer <token>} alla richiesta
     * multipart, oltre all'authorizationToken nel body come parametro.
     *
     * @param uploadUrl          URL di upload sulla PU
     * @param authorizationToken token JWT di autorizzazione generato dalla PU
     * @param file               il file caricato dal SIL
     * @param codIpaEnte         codice IPA dell'ente (per ottenere il token OAuth2)
     * @return la risposta della PU come stringa (JSON)
     * @throws PiattaformaCommunicationException  in caso di errore di comunicazione
     * @throws PiattaformaAuthenticationException in caso di errore di autenticazione OAuth2
     */
    @CircuitBreaker(name = "piattaformaUnitaria", fallbackMethod = "forwardUploadPuFallback")
    @Retry(name = "piattaformaUnitaria")
    public String inoltraAllaPU(String uploadUrl, String authorizationToken,
                                MultipartFile file, String codIpaEnte) {
        log.info("Inoltro file upload alla Piattaforma Unitaria: {} (ente: '{}', file: '{}', dimensione: {} byte)",
                uploadUrl, codIpaEnte, file.getOriginalFilename(), file.getSize());

        // Recupera le credenziali OAuth2 dell'ente dalla cache
        EnteCompleto ente = enteCacheService.findByCodIpaEnte(codIpaEnte)
                .orElseThrow(() -> new PiattaformaCommunicationException(
                        "Ente '" + codIpaEnte + "' non trovato nella cache per l'upload alla PU"));

        // Ottiene il token OAuth2 Bearer per l'ente
        String oauthToken = oAuthTokenService.getAccessToken(
                codIpaEnte, ente.getClientId(), ente.getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(oauthToken);

        MultiValueMap<String, Object> body = costruisciBodyMultipart(authorizationToken, file);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            return eseguiRichiestaUpload(uploadUrl, request, "PU");
        } catch (PiattaformaCommunicationException e) {
            // Se riceve un 401, tenta con un token refreshato (il token potrebbe essere scaduto)
            if (e.getHttpStatus() == 401) {
                log.warn("Ricevuto 401 dalla PU per upload. Retry con token refreshato per ente '{}'",
                        codIpaEnte);
                String nuovoToken = oAuthTokenService.refreshToken(
                        codIpaEnte, ente.getClientId(), ente.getClientSecret());
                headers.setBearerAuth(nuovoToken);
                HttpEntity<MultiValueMap<String, Object>> retryRequest = new HttpEntity<>(body, headers);
                return eseguiRichiestaUpload(uploadUrl, retryRequest, "PU (retry post-401)");
            }
            throw e;
        }
    }

    /**
     * Costruisce il body multipart per la richiesta di upload.
     *
     * <p>Il formato replica quello atteso da {@code MyBoxController.uploadByWS}:
     * <ul>
     *   <li>{@code authorizationToken}: parametro stringa con il JWT</li>
     *   <li>{@code file}: il file binario con il nome originale</li>
     * </ul>
     *
     * @param authorizationToken il JWT di autorizzazione
     * @param file               il file da caricare
     * @return il body multipart pronto per la richiesta HTTP
     */
    private MultiValueMap<String, Object> costruisciBodyMultipart(String authorizationToken,
                                                                   MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("authorizationToken", authorizationToken);

        try {
            // Usa ByteArrayResource con override di getFilename() per preservare il nome del file
            // nel Content-Disposition della parte multipart
            final String nomeFile = file.getOriginalFilename();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return nomeFile;
                }
            };

            // Aggiunge il file con i Content-Type corretti per la parte multipart
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);
            body.add("file", filePart);

        } catch (IOException e) {
            throw new PiattaformaCommunicationException(
                    "Errore nella lettura del file per l'inoltro dell'upload: " + e.getMessage(), e);
        }

        return body;
    }

    /**
     * Esegue la richiesta HTTP POST multipart di upload verso il backend.
     *
     * @param url         URL del backend di destinazione
     * @param request     la richiesta multipart con headers e body
     * @param destinazione etichetta descrittiva per il logging (es. "legacy", "PU")
     * @return il corpo della risposta del backend come stringa
     * @throws PiattaformaCommunicationException in caso di errore HTTP o di rete
     */
    private String eseguiRichiestaUpload(String url,
                                          HttpEntity<MultiValueMap<String, Object>> request,
                                          String destinazione) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Risposta ricevuta dall'upload verso {}: Status {}", destinazione, response.getStatusCode());
            log.debug("Corpo risposta upload ({}): {}", destinazione, response.getBody());
            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Errore HTTP nell'upload verso {}: {} - {}",
                    destinazione, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PiattaformaCommunicationException(
                    "Errore HTTP " + e.getStatusCode().value() + " nell'upload verso " + destinazione,
                    e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            log.error("Timeout o errore di rete nell'upload verso {}: {}", destinazione, e.getMessage());
            throw new PiattaformaCommunicationException(
                    "Timeout o errore di rete nell'upload verso " + destinazione, e);

        } catch (RestClientException e) {
            log.error("Errore nella comunicazione per l'upload verso {}: {}", destinazione, e.getMessage(), e);
            throw new PiattaformaCommunicationException(
                    "Errore nella comunicazione per l'upload verso " + destinazione, e);
        }
    }

    /**
     * Fallback per upload verso backend legacy (circuit breaker aperto o retry esaurito).
     *
     * @param uploadUrl          URL dell'upload (non utilizzato nel fallback)
     * @param authorizationToken token di autorizzazione (non utilizzato nel fallback)
     * @param file               file da caricare (non utilizzato nel fallback)
     * @param ex                 l'eccezione che ha attivato il fallback
     * @throws PiattaformaCommunicationException sempre, con status 503
     */
    public String forwardUploadFallback(String uploadUrl, String authorizationToken,
                                        MultipartFile file, Throwable ex) {
        log.error("Circuit breaker aperto o retry esaurito per upload verso backend legacy. Causa: {}",
                ex.getMessage());
        throw new PiattaformaCommunicationException(
                "Backend legacy temporaneamente non raggiungibile per l'upload (circuit breaker aperto).", 503);
    }

    /**
     * Fallback per upload verso PU (circuit breaker aperto o retry esaurito).
     *
     * @param uploadUrl          URL dell'upload (non utilizzato nel fallback)
     * @param authorizationToken token di autorizzazione (non utilizzato nel fallback)
     * @param file               file da caricare (non utilizzato nel fallback)
     * @param codIpaEnte         codice IPA dell'ente (non utilizzato nel fallback)
     * @param ex                 l'eccezione che ha attivato il fallback
     * @throws PiattaformaCommunicationException sempre, con status 503
     */
    public String forwardUploadPuFallback(String uploadUrl, String authorizationToken,
                                          MultipartFile file, String codIpaEnte, Throwable ex) {
        log.error("Circuit breaker aperto o retry esaurito per upload verso PU per ente '{}'. Causa: {}",
                codIpaEnte, ex.getMessage());
        throw new PiattaformaCommunicationException(
                "Piattaforma Unitaria temporaneamente non raggiungibile per l'upload (circuit breaker aperto).", 503);
    }
}
