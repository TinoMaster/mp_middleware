package it.ariaspa.mypay.mypaycore.api.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenService;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import it.ariaspa.mypay.mypaycore.api.config.PiattaformaUnitariaConfig;
import it.ariaspa.mypay.mypaycore.api.domain.EnteCompleto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Client HTTP per la comunicazione con la Piattaforma Unitaria (pagoPA).
 *
 * <p>Responsabilita':
 * <ul>
 *   <li>Inoltrare le richieste SOAP ricevute dai SIL verso la piattaforma</li>
 *   <li>Aggiungere manualmente l'header {@code Authorization: Bearer <token>} per-ente</li>
 *   <li>Gestire il retry del token in caso di risposta 401 Unauthorized</li>
 *   <li>Restituire la risposta della piattaforma al chiamante</li>
 * </ul>
 *
 * <p>L'autenticazione e' <strong>per-ente</strong>: ogni ente ha il proprio token OAuth2,
 * ottenuto tramite {@link OAuthTokenService#getAccessToken(String, String, String)} con le
 * credenziali ({@code client_id}, {@code client_secret}) specifiche dell'ente memorizzate
 * nella tabella {@code mygov_ente_config_pu}.
 *
 * <p>Resilienza (Resilience4j):
 * <ul>
 *   <li>Circuit Breaker: apre il circuito dopo troppi errori consecutivi</li>
 *   <li>Retry: tentativi con backoff esponenziale per errori temporanei</li>
 * </ul>
 */
@Service
public class PiattaformaUnitariaClient {

    private static final Logger log = LoggerFactory.getLogger(PiattaformaUnitariaClient.class);

    /** Timeout di connessione per le richieste verso la piattaforma (millisecondi). */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Timeout di lettura per le richieste verso la piattaforma (millisecondi). */
    private static final int READ_TIMEOUT_MS = 30_000;

    private final PiattaformaUnitariaConfig config;
    private final OAuthTokenService oAuthTokenService;
    private RestTemplate restTemplate;

    @Autowired
    public PiattaformaUnitariaClient(PiattaformaUnitariaConfig config,
                                     OAuthTokenService oAuthTokenService) {
        this.config = config;
        this.oAuthTokenService = oAuthTokenService;
    }

    /**
     * Inizializza il RestTemplate con i timeout configurati.
     * Il Bearer token viene aggiunto manualmente per-ente ad ogni richiesta.
     */
    @PostConstruct
    public void init() {
        if (this.restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(READ_TIMEOUT_MS);
            this.restTemplate = new RestTemplate(factory);
        }
        log.info("PiattaformaUnitariaClient inizializzato. Base URL: {}, " +
                        "connectTimeout: {}ms, readTimeout: {}ms",
                config.getBaseUrl(), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /**
     * Inoltra una richiesta SOAP alla Piattaforma Unitaria per conto di un ente specifico.
     *
     * <p>Il metodo ottiene il token OAuth2 per-ente tramite {@link OAuthTokenService},
     * lo aggiunge come header {@code Authorization: Bearer}, e invia il payload XML/SOAP
     * alla piattaforma. In caso di risposta 401, effettua un retry con un nuovo token.
     *
     * <p>Resilienza:
     * <ul>
     *   <li>CircuitBreaker "piattaformaUnitaria": protegge da errori continuativi</li>
     *   <li>Retry "piattaformaUnitaria": retry con backoff esponenziale per errori temporanei</li>
     * </ul>
     *
     * @param path      il percorso relativo dell'endpoint sulla piattaforma
     * @param soapXml   il corpo XML/SOAP della richiesta
     * @param ente      i dati dell'ente, incluse le credenziali OAuth2 per-ente
     * @return la risposta XML/SOAP della piattaforma
     * @throws PiattaformaCommunicationException  in caso di errore nella comunicazione
     * @throws PiattaformaAuthenticationException in caso di errore di autenticazione persistente
     */
    @CircuitBreaker(name = "piattaformaUnitaria", fallbackMethod = "forwardSoapRequestFallback")
    @Retry(name = "piattaformaUnitaria")
    public String forwardSoapRequest(String path, String soapXml, EnteCompleto ente) {
        String url = config.getBaseUrl() + path;
        String codIpaEnte = ente.getCodIpaEnte();

        // Ottiene il token OAuth2 per-ente (dalla cache o richiedendone uno nuovo)
        String token = oAuthTokenService.getAccessToken(
                codIpaEnte, ente.getClientId(), ente.getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.setBearerAuth(token);

        HttpEntity<String> request = new HttpEntity<>(soapXml, headers);

        log.info("Inoltro richiesta SOAP alla Piattaforma Unitaria: {} (ente: '{}')", url, codIpaEnte);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Risposta ricevuta dalla Piattaforma Unitaria per ente '{}'. Status: {}",
                    codIpaEnte, response.getStatusCode());
            log.debug("Corpo risposta: {}", response.getBody());

            return response.getBody();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Ricevuto 401 Unauthorized dalla Piattaforma Unitaria per ente '{}'. "
                        + "Tentativo di refresh del token e retry della richiesta.", codIpaEnte);
                return retryWithNewToken(url, soapXml, ente);
            }
            log.error("Errore HTTP dalla Piattaforma Unitaria per ente '{}': {} - {}",
                    codIpaEnte, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PiattaformaCommunicationException(
                    "Errore HTTP dalla Piattaforma Unitaria",
                    e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            log.error("Timeout o errore di rete verso la Piattaforma Unitaria per ente '{}': {}",
                    codIpaEnte, e.getMessage());
            throw new PiattaformaCommunicationException(
                    "Timeout o errore di rete verso la Piattaforma Unitaria", e);

        } catch (RestClientException e) {
            log.error("Errore nella comunicazione con la Piattaforma Unitaria per ente '{}': {}",
                    codIpaEnte, e.getMessage(), e);
            throw new PiattaformaCommunicationException(
                    "Errore nella comunicazione con la Piattaforma Unitaria", e);
        }
    }

    /**
     * Fallback invocato quando il circuit breaker e' aperto.
     *
     * @param path    il percorso originale della richiesta
     * @param soapXml il corpo SOAP originale
     * @param ente    i dati dell'ente
     * @param ex      l'eccezione che ha causato l'apertura del circuit breaker
     * @return mai — lancia sempre {@link PiattaformaCommunicationException}
     */
    public String forwardSoapRequestFallback(String path, String soapXml, EnteCompleto ente,
                                             Throwable ex) {
        log.error("Circuit breaker aperto per la Piattaforma Unitaria. "
                + "Richiesta a {} per ente '{}' rifiutata. Causa: {}",
                path, ente.getCodIpaEnte(), ex.getMessage());
        // Il circuit breaker e' aperto: dal punto di vista del SIL il servizio e' temporaneamente
        // non disponibile (503). Non propagare l'HTTP status dell'errore originale che ha causato
        // l'apertura del circuito: quello era la causa storica, non lo stato attuale.
        throw new PiattaformaCommunicationException(
                "Piattaforma Unitaria temporaneamente non raggiungibile (circuit breaker aperto). "
                        + "Riprovare piu' tardi.",
                503);
    }

    /**
     * Effettua un retry della richiesta dopo aver ottenuto un nuovo token OAuth2.
     * Utilizzato quando la piattaforma risponde con 401 Unauthorized.
     *
     * @param url     l'URL completo della richiesta
     * @param soapXml il corpo SOAP della richiesta
     * @param ente    i dati dell'ente con le credenziali OAuth2
     * @return la risposta XML/SOAP della piattaforma
     */
    private String retryWithNewToken(String url, String soapXml, EnteCompleto ente) {
        String codIpaEnte = ente.getCodIpaEnte();

        // Forza il refresh del token per questo ente
        String nuovoToken = oAuthTokenService.refreshToken(
                codIpaEnte, ente.getClientId(), ente.getClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.setBearerAuth(nuovoToken);
        HttpEntity<String> request = new HttpEntity<>(soapXml, headers);

        log.info("Retry della richiesta SOAP con nuovo token per ente '{}': {}", codIpaEnte, url);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Risposta ricevuta dopo retry per ente '{}'. Status: {}",
                    codIpaEnte, response.getStatusCode());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Secondo tentativo fallito per ente '{}' con errore HTTP: {} - {}",
                    codIpaEnte, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PiattaformaAuthenticationException(
                    "Autenticazione fallita anche dopo refresh del token per ente '"
                            + codIpaEnte + "'. HTTP " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Secondo tentativo fallito per ente '{}': {}", codIpaEnte, e.getMessage(), e);
            throw new PiattaformaCommunicationException(
                    "Comunicazione fallita anche dopo refresh del token per ente '" + codIpaEnte + "'", e);
        }
    }
}
