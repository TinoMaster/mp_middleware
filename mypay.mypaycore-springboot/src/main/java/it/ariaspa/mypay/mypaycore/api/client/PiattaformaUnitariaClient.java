package it.ariaspa.mypay.mypaycore.api.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenInterceptor;
import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenService;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import it.ariaspa.mypay.mypaycore.api.config.PiattaformaUnitariaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;

/**
 * Client HTTP per la comunicazione con la Piattaforma Unitaria (pagoPA).
 *
 * Responsabilita:
 * - Inoltrare le richieste SOAP ricevute dai SIL verso la piattaforma
 * - Gestire l'autenticazione tramite OAuthTokenInterceptor (Bearer token automatico)
 * - Gestire il retry del token in caso di risposta 401 Unauthorized
 * - Restituire la risposta della piattaforma al chiamante
 *
 * Resilienza (Resilience4j):
 * - Circuit Breaker: apre il circuito dopo troppi errori consecutivi
 * - Retry: tentativi con backoff esponenziale per errori temporanei
 *
 * Il RestTemplate interno e configurato con:
 * - OAuthTokenInterceptor per aggiungere automaticamente l'header Authorization Bearer
 * - Timeout di connessione e lettura configurabili
 */
@Service
public class PiattaformaUnitariaClient {

    private static final Logger log = LoggerFactory.getLogger(PiattaformaUnitariaClient.class);

    /** Timeout di connessione per le richieste verso la piattaforma (millisecondi). */
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Timeout di lettura per le richieste verso la piattaforma (millisecondi). */
    private static final int READ_TIMEOUT_MS = 30_000;

    private final PiattaformaUnitariaConfig config;
    private final OAuthTokenInterceptor oAuthTokenInterceptor;
    private final OAuthTokenService oAuthTokenService;
    private RestTemplate restTemplate;

    public PiattaformaUnitariaClient(PiattaformaUnitariaConfig config,
                                      OAuthTokenInterceptor oAuthTokenInterceptor,
                                      OAuthTokenService oAuthTokenService) {
        this.config = config;
        this.oAuthTokenInterceptor = oAuthTokenInterceptor;
        this.oAuthTokenService = oAuthTokenService;
    }

    /**
     * Costruttore per testing che consente di iniettare un RestTemplate mock.
     */
    PiattaformaUnitariaClient(PiattaformaUnitariaConfig config,
                               OAuthTokenInterceptor oAuthTokenInterceptor,
                               OAuthTokenService oAuthTokenService,
                               RestTemplate restTemplate) {
        this.config = config;
        this.oAuthTokenInterceptor = oAuthTokenInterceptor;
        this.oAuthTokenService = oAuthTokenService;
        this.restTemplate = restTemplate;
    }

    /**
     * Inizializza il RestTemplate con l'interceptor OAuth2 e i timeout configurati.
     */
    @PostConstruct
    public void init() {
        if (this.restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(READ_TIMEOUT_MS);
            this.restTemplate = new RestTemplate(factory);
        }
        this.restTemplate.setInterceptors(List.of(oAuthTokenInterceptor));
        log.info("PiattaformaUnitariaClient inizializzato. Base URL: {}, " +
                        "connectTimeout: {}ms, readTimeout: {}ms",
                config.getBaseUrl(), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /**
     * Inoltra una richiesta SOAP alla Piattaforma Unitaria.
     *
     * Il metodo invia il payload XML/SOAP alla piattaforma utilizzando
     * il token OAuth2 per l'autenticazione. In caso di risposta 401,
     * effettua un retry con un nuovo token.
     *
     * Resilienza:
     * - CircuitBreaker "piattaformaUnitaria": protegge da errori continuativi
     * - Retry "piattaformaUnitaria": retry con backoff esponenziale per errori temporanei
     *
     * @param path    il percorso relativo dell'endpoint sulla piattaforma
     * @param soapXml il corpo XML/SOAP della richiesta
     * @return la risposta XML/SOAP della piattaforma
     * @throws PiattaformaCommunicationException in caso di errore nella comunicazione
     * @throws PiattaformaAuthenticationException in caso di errore di autenticazione persistente
     */
    @CircuitBreaker(name = "piattaformaUnitaria", fallbackMethod = "forwardSoapRequestFallback")
    @Retry(name = "piattaformaUnitaria")
    public String forwardSoapRequest(String path, String soapXml) {
        String url = config.getBaseUrl() + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);

        HttpEntity<String> request = new HttpEntity<>(soapXml, headers);

        log.info("Inoltro richiesta SOAP alla Piattaforma Unitaria: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            log.info("Risposta ricevuta dalla Piattaforma Unitaria. Status: {}", response.getStatusCode());
            log.debug("Corpo risposta: {}", response.getBody());

            return response.getBody();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Ricevuto 401 Unauthorized dalla Piattaforma Unitaria. " +
                        "Tentativo di refresh del token e retry della richiesta.");
                return retryWithNewToken(url, request);
            }
            log.error("Errore HTTP dalla Piattaforma Unitaria: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new PiattaformaCommunicationException(
                    "Errore HTTP dalla Piattaforma Unitaria",
                    e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            log.error("Timeout o errore di rete verso la Piattaforma Unitaria: {}", e.getMessage());
            throw new PiattaformaCommunicationException(
                    "Timeout o errore di rete verso la Piattaforma Unitaria", e);

        } catch (RestClientException e) {
            log.error("Errore nella comunicazione con la Piattaforma Unitaria: {}", e.getMessage(), e);
            throw new PiattaformaCommunicationException(
                    "Errore nella comunicazione con la Piattaforma Unitaria", e);
        }
    }

    /**
     * Fallback invocato quando il circuit breaker e aperto.
     *
     * @param path    il percorso originale della richiesta
     * @param soapXml il corpo SOAP originale
     * @param ex      l'eccezione che ha causato l'apertura del circuit breaker
     * @return mai - lancia sempre PiattaformaCommunicationException
     */
    public String forwardSoapRequestFallback(String path, String soapXml, Throwable ex) {
        log.error("Circuit breaker aperto per la Piattaforma Unitaria. " +
                "Richiesta a {} rifiutata. Causa: {}", path, ex.getMessage());
        throw new PiattaformaCommunicationException(
                "Piattaforma Unitaria temporaneamente non raggiungibile (circuit breaker aperto). " +
                        "Riprovare piu tardi.",
                ex instanceof PiattaformaCommunicationException
                        ? ((PiattaformaCommunicationException) ex).getHttpStatus()
                        : 503);
    }

    /**
     * Effettua un retry della richiesta dopo aver ottenuto un nuovo token OAuth2.
     * Utilizzato quando la piattaforma risponde con 401 Unauthorized.
     */
    private String retryWithNewToken(String url, HttpEntity<String> request) {
        oAuthTokenService.refreshToken();

        log.info("Retry della richiesta SOAP con nuovo token: {}", url);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Risposta ricevuta dopo retry. Status: {}", response.getStatusCode());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Secondo tentativo fallito con errore HTTP: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new PiattaformaAuthenticationException(
                    "Autenticazione fallita anche dopo refresh del token. HTTP " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Secondo tentativo fallito: {}", e.getMessage(), e);
            throw new PiattaformaCommunicationException(
                    "Comunicazione fallita anche dopo refresh del token", e);
        }
    }
}
