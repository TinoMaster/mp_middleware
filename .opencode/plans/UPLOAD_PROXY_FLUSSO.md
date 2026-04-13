# Piano: Proxy Upload Flusso Import

## Obiettivo

Intercettare la risposta di `paaSILAutorizzaImportFlusso` per sostituire la `uploadUrl` del backend con un URL del middleware, salvare l'URL originale, e creare un endpoint REST che riceve il file dal SIL e lo inoltra al backend corretto (legacy o PU).

---

## Flusso Target

```
1. SIL → SOAP(paaSILAutorizzaImportFlusso) → Middleware → Backend (legacy/PU)
2. Backend risponde con: uploadUrl, authorizationToken, requestToken, importPath
3. Middleware:
   a. Salva in cache: authorizationToken → {uploadUrl originale, modalitaRouting, codIpaEnte}
   b. Sostituisce uploadUrl nella risposta con URL del middleware
   c. Restituisce la risposta modificata al SIL
4. SIL → POST file a middleware (uploadUrl del middleware) con authorizationToken + file
5. Middleware:
   a. Recupera dalla cache l'entry associata all'authorizationToken
   b. Inoltra il file all'uploadUrl originale del backend:
      - Legacy: POST multipart (authorizationToken + file, senza OAuth2)
      - PU: POST multipart (authorizationToken + file, CON OAuth2 Bearer)
   c. Restituisce la risposta del backend al SIL
```

---

## Decisioni Prese

| Aspetto | Decisione |
|---------|-----------|
| Storage uploadUrl | Cache in memoria (ConcurrentHashMap) con TTL |
| Chiave cache | `authorizationToken` (il SIL lo invia come parametro) |
| Dipendenza REST | Aggiungere `spring-boot-starter-web` |
| Auth upload PU | OAuth2 Bearer + authorizationToken nel body multipart |
| Routing upload | Solo al sistema corretto per l'ente (come routing SOAP) |
| Post-processing | Usare `processRequest()` prima, poi post-elaborare la risposta |

---

## Struttura della risposta paaSILAutorizzaImportFlussoRisposta

```xml
<paaSILAutorizzaImportFlussoRisposta>
  <fault>...</fault>                    <!-- opzionale, da risposta base -->
  <uploadUrl>https://backend/uploadFlusso</uploadUrl>
  <authorizationToken>jwt.token.here</authorizationToken>
  <requestToken>uuid-random</requestToken>
  <importPath>/IMPORT</importPath>
</paaSILAutorizzaImportFlussoRisposta>
```

---

## File da Creare/Modificare

### 1. POM — Aggiungere spring-boot-starter-web

**File**: `mypay.mypaycore-springboot/pom.xml`

Dopo la dipendenza `spring-boot-starter-web-services` (riga 78), aggiungere:

```xml
<!-- Spring MVC: per esporre endpoint REST (upload proxy per i flussi di import) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 2. UploadProxyEntry — DTO per la cache

**File NUOVO**: `api/upload/UploadProxyEntry.java`

```java
package it.ariaspa.mypay.mypaycore.api.upload;

import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;

import java.time.Instant;

/**
 * DTO immutabile che rappresenta un'entry nella cache del proxy upload.
 *
 * <p>Salva le informazioni necessarie per inoltrare il file di upload
 * dal SIL al backend corretto (legacy o PU) quando il SIL chiama
 * l'endpoint REST di upload del middleware.
 *
 * <p>La chiave di lookup nella cache è l'{@code authorizationToken}
 * restituito dalla risposta di {@code paaSILAutorizzaImportFlusso}.
 *
 * @see UploadProxyCacheService
 */
public class UploadProxyEntry {

    /** URL originale di upload restituita dal backend (legacy o PU). */
    private final String uploadUrlOriginale;

    /** Token JWT di autorizzazione generato dal backend per l'upload. */
    private final String authorizationToken;

    /** Token univoco di richiesta per tracciare l'import. */
    private final String requestToken;

    /** Percorso relativo per l'import del flusso. */
    private final String importPath;

    /** Modalità di routing: PIATTAFORMA_UNITARIA o LEGACY. */
    private final ModalitaRouting modalitaRouting;

    /** Codice IPA dell'ente che ha effettuato la richiesta. */
    private final String codIpaEnte;

    /** Timestamp di creazione dell'entry (per TTL). */
    private final Instant timestampCreazione;

    /**
     * Crea una nuova entry per la cache del proxy upload.
     *
     * @param uploadUrlOriginale URL originale di upload dal backend
     * @param authorizationToken token JWT di autorizzazione
     * @param requestToken       token univoco della richiesta
     * @param importPath         percorso relativo per l'import
     * @param modalitaRouting    modalità di routing (PU o LEGACY)
     * @param codIpaEnte         codice IPA dell'ente
     */
    public UploadProxyEntry(String uploadUrlOriginale,
                            String authorizationToken,
                            String requestToken,
                            String importPath,
                            ModalitaRouting modalitaRouting,
                            String codIpaEnte) {
        this.uploadUrlOriginale = uploadUrlOriginale;
        this.authorizationToken = authorizationToken;
        this.requestToken = requestToken;
        this.importPath = importPath;
        this.modalitaRouting = modalitaRouting;
        this.codIpaEnte = codIpaEnte;
        this.timestampCreazione = Instant.now();
    }

    public String getUploadUrlOriginale() { return uploadUrlOriginale; }
    public String getAuthorizationToken() { return authorizationToken; }
    public String getRequestToken() { return requestToken; }
    public String getImportPath() { return importPath; }
    public ModalitaRouting getModalitaRouting() { return modalitaRouting; }
    public String getCodIpaEnte() { return codIpaEnte; }
    public Instant getTimestampCreazione() { return timestampCreazione; }

    /**
     * Verifica se l'entry è scaduta in base al TTL specificato.
     *
     * @param ttlSecondi durata massima di validità in secondi
     * @return {@code true} se l'entry è scaduta
     */
    public boolean isScaduta(long ttlSecondi) {
        return Instant.now().isAfter(timestampCreazione.plusSeconds(ttlSecondi));
    }

    /**
     * Verifica se il routing è verso la Piattaforma Unitaria.
     *
     * @return {@code true} se il routing è verso PU
     */
    public boolean isPiattaformaUnitaria() {
        return modalitaRouting == ModalitaRouting.PIATTAFORMA_UNITARIA;
    }

    @Override
    public String toString() {
        return "UploadProxyEntry{" +
                "codIpaEnte='" + codIpaEnte + '\'' +
                ", modalitaRouting=" + modalitaRouting +
                ", requestToken='" + requestToken + '\'' +
                ", timestampCreazione=" + timestampCreazione +
                '}';
        // uploadUrlOriginale e authorizationToken esclusi per sicurezza
    }
}
```

### 3. UploadProxyCacheService — Cache in memoria con TTL

**File NUOVO**: `api/upload/UploadProxyCacheService.java`

```java
package it.ariaspa.mypay.mypaycore.api.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servizio di cache in memoria per le entry del proxy upload.
 *
 * <p>Salva le associazioni {@code authorizationToken → UploadProxyEntry}
 * generate durante il post-processing della risposta di
 * {@code paaSILAutorizzaImportFlusso}. Quando il SIL chiama l'endpoint
 * di upload del middleware, l'entry viene recuperata e rimossa dalla cache.
 *
 * <p>Caratteristiche:
 * <ul>
 *   <li>Thread-safe tramite {@link ConcurrentHashMap}</li>
 *   <li>TTL configurabile per ogni entry</li>
 *   <li>Pulizia periodica delle entry scadute tramite {@code @Scheduled}</li>
 *   <li>Rimozione automatica dopo il primo utilizzo (one-shot)</li>
 * </ul>
 *
 * @see UploadProxyEntry
 */
@Service
public class UploadProxyCacheService {

    private static final Logger log = LoggerFactory.getLogger(UploadProxyCacheService.class);

    /**
     * Cache: authorizationToken → UploadProxyEntry.
     * L'authorizationToken è un JWT generato dal backend (univoco per richiesta).
     */
    private final ConcurrentHashMap<String, UploadProxyEntry> cache = new ConcurrentHashMap<>();

    /**
     * TTL in secondi per le entry nella cache.
     * Default: 3600 secondi (1 ora).
     */
    private final long ttlSecondi;

    public UploadProxyCacheService(
            @Value("${middleware.upload.proxy.cache-ttl-seconds:3600}") long ttlSecondi) {
        this.ttlSecondi = ttlSecondi;
        log.info("UploadProxyCacheService inizializzato con TTL: {} secondi", ttlSecondi);
    }

    /**
     * Salva un'entry nella cache.
     *
     * @param authorizationToken chiave di lookup (JWT generato dal backend)
     * @param entry              i dati dell'upload proxy da salvare
     */
    public void salva(String authorizationToken, UploadProxyEntry entry) {
        cache.put(authorizationToken, entry);
        log.info("Entry salvata nella cache upload proxy per ente '{}', requestToken '{}'",
                entry.getCodIpaEnte(), entry.getRequestToken());
        log.debug("Dimensione cache upload proxy: {}", cache.size());
    }

    /**
     * Recupera e rimuove un'entry dalla cache (one-shot).
     *
     * <p>L'entry viene rimossa dopo il primo recupero per evitare
     * riutilizzi dello stesso authorizationToken.
     *
     * @param authorizationToken chiave di lookup (JWT del backend)
     * @return l'entry se presente e non scaduta, {@code Optional.empty()} altrimenti
     */
    public Optional<UploadProxyEntry> recuperaERimuovi(String authorizationToken) {
        UploadProxyEntry entry = cache.remove(authorizationToken);

        if (entry == null) {
            log.warn("Nessuna entry trovata nella cache upload proxy per l'authorizationToken fornito");
            return Optional.empty();
        }

        if (entry.isScaduta(ttlSecondi)) {
            log.warn("Entry scaduta nella cache upload proxy per ente '{}', requestToken '{}'",
                    entry.getCodIpaEnte(), entry.getRequestToken());
            return Optional.empty();
        }

        log.info("Entry recuperata dalla cache upload proxy per ente '{}', requestToken '{}'",
                entry.getCodIpaEnte(), entry.getRequestToken());
        return Optional.of(entry);
    }

    /**
     * Pulizia periodica delle entry scadute dalla cache.
     * Eseguita ogni 5 minuti per evitare memory leak.
     */
    @Scheduled(fixedDelayString = "${middleware.upload.proxy.cleanup-interval-ms:300000}")
    public void puliziaEntryScadute() {
        int primaDellaPulizia = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isScaduta(ttlSecondi));
        int rimosse = primaDellaPulizia - cache.size();
        if (rimosse > 0) {
            log.info("Pulizia cache upload proxy: {} entry scadute rimosse su {} totali",
                    rimosse, primaDellaPulizia);
        }
    }

    /**
     * Restituisce il numero di entry nella cache (per health check e metriche).
     *
     * @return numero di entry presenti
     */
    public int dimensioneCache() {
        return cache.size();
    }
}
```

**NOTA**: Per far funzionare `@Scheduled` bisogna aggiungere `@EnableScheduling` in una classe di configurazione. Verificare se è già presente (probabilmente in `Application.java` o in una config). Se non c'è, aggiungerlo.

### 4. Modificare PagamentiTelematiciDovutiPagatiEndpoint — Post-processing

**File MODIFICATO**: `api/soap/endpoint/mypay/PagamentiTelematiciDovutiPagatiEndpoint.java`

Le modifiche da fare:

1. **Aggiungere dipendenze nel costruttore**: `UploadProxyCacheService` e il valore di configurazione `middleware.upload.proxy.base-url`
2. **Modificare il metodo `paaSILAutorizzaImportFlusso`**: usare `processRequest()` e poi post-processare la risposta

```java
package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypay;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.logging.TransactionLoggingService;
import it.ariaspa.mypay.mypaycore.api.metrics.MiddlewareMetricsService;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecisionService;
import it.ariaspa.mypay.mypaycore.api.soap.endpoint.AbstractSoapProxyEndpoint;
import it.ariaspa.mypay.mypaycore.api.upload.UploadProxyCacheService;
import it.ariaspa.mypay.mypaycore.api.upload.UploadProxyEntry;
import it.ariaspa.mypay.mypaycore.api.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Endpoint
public class PagamentiTelematiciDovutiPagatiEndpoint extends AbstractSoapProxyEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PagamentiTelematiciDovutiPagatiEndpoint.class);

    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/ente/";
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH_PU_MYPAY;
    private static final String DEFAULT_PATH = Constants.DEFAULT_PATH_PA;

    /** Servizio di cache per le entry del proxy upload. */
    private final UploadProxyCacheService uploadProxyCacheService;

    /** URL base del middleware esposto ai SIL (es. https://middleware.example.com). */
    private final String middlewareUploadBaseUrl;

    /** Path dell'endpoint REST di upload del middleware. */
    public static final String UPLOAD_FLUSSO_PATH = "/api/upload/flusso";

    public PagamentiTelematiciDovutiPagatiEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService,
            UploadProxyCacheService uploadProxyCacheService,
            @Value("${middleware.upload.proxy.base-url}") String middlewareUploadBaseUrl) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
        this.uploadProxyCacheService = uploadProxyCacheService;
        this.middlewareUploadBaseUrl = middlewareUploadBaseUrl;
    }

    // --- 1. paaSILImportaDovuto --- (INVARIATO)
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILImportaDovuto")
    @ResponsePayload
    public Element paaSILImportaDovuto(@RequestPayload Element request,
                                       MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. paaSILAutorizzaImportFlusso --- (MODIFICATO: post-processing)
    /**
     * Operazione paaSILAutorizzaImportFlusso con post-processing della risposta.
     *
     * <p>Dopo aver ricevuto la risposta dal backend (tramite {@code processRequest}),
     * questo metodo:
     * <ol>
     *   <li>Estrae i campi {@code uploadUrl}, {@code authorizationToken},
     *       {@code requestToken} e {@code importPath} dalla risposta</li>
     *   <li>Salva l'URL originale nella cache del proxy upload</li>
     *   <li>Sostituisce {@code uploadUrl} con l'URL dell'endpoint REST del middleware</li>
     * </ol>
     *
     * <p>Questo consente al middleware di fare da proxy anche per l'upload del file,
     * garantendo che il SIL non debba comunicare direttamente con il backend.
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILAutorizzaImportFlusso")
    @ResponsePayload
    public Element paaSILAutorizzaImportFlusso(@RequestPayload Element request,
                                                MessageContext messageContext) {
        // Step 1: Processa la richiesta normalmente (routing + inoltro al backend)
        Element responseElement = processRequest(request, messageContext, PLATFORM_PATH);

        // Step 2: Post-processing — intercetta e modifica la uploadUrl
        try {
            postProcessAutorizzaImportFlusso(responseElement, request, messageContext);
        } catch (Exception e) {
            // Se il post-processing fallisce, logga ma restituisci la risposta originale.
            // Non vogliamo bloccare il flusso se il post-processing ha un problema.
            log.error("Errore nel post-processing di paaSILAutorizzaImportFlusso. "
                    + "La risposta viene restituita senza modifiche. Errore: {}", e.getMessage(), e);
        }

        return responseElement;
    }

    /**
     * Post-processa la risposta di paaSILAutorizzaImportFlusso.
     *
     * <p>Estrae i 4 campi dalla risposta, salva l'URL originale nella cache,
     * e sostituisce la uploadUrl con quella del middleware.
     *
     * @param responseElement la risposta XML dal backend
     * @param requestElement  la richiesta XML originale (per estrarre codIpaEnte)
     * @param messageContext  il contesto del messaggio SOAP
     */
    private void postProcessAutorizzaImportFlusso(Element responseElement,
                                                   Element requestElement,
                                                   MessageContext messageContext) {
        // Estrai i campi dalla risposta
        String uploadUrl = estraiTestoTag(responseElement, "uploadUrl");
        String authorizationToken = estraiTestoTag(responseElement, "authorizationToken");
        String requestToken = estraiTestoTag(responseElement, "requestToken");
        String importPath = estraiTestoTag(responseElement, "importPath");

        // Se manca la uploadUrl, la risposta potrebbe contenere un fault — non processare
        if (uploadUrl == null || authorizationToken == null) {
            log.debug("Risposta di paaSILAutorizzaImportFlusso senza uploadUrl o authorizationToken. "
                    + "Probabile fault dal backend — nessun post-processing necessario.");
            return;
        }

        // Determina la modalità di routing per questo ente
        // Il codIpaEnte viene estratto dalla richiesta SOAP originale
        String codIpaEnte = estraiCodIpaEnteDaRichiesta(messageContext);
        ModalitaRouting modalitaRouting = determinaModalitaRouting(codIpaEnte);

        // Salva nella cache l'URL originale associata all'authorizationToken
        UploadProxyEntry entry = new UploadProxyEntry(
                uploadUrl, authorizationToken, requestToken, importPath,
                modalitaRouting, codIpaEnte);
        uploadProxyCacheService.salva(authorizationToken, entry);

        // Sostituisci la uploadUrl nella risposta con l'URL del middleware
        String middlewareUploadUrl = middlewareUploadBaseUrl + UPLOAD_FLUSSO_PATH;
        sostituisciTestoTag(responseElement, "uploadUrl", middlewareUploadUrl);

        log.info("Post-processing paaSILAutorizzaImportFlusso completato per ente '{}'. "
                + "uploadUrl sostituita: '{}' → '{}'",
                codIpaEnte, uploadUrl, middlewareUploadUrl);
    }

    /**
     * Estrae il codice IPA dell'ente dal contesto del messaggio SOAP.
     *
     * @param messageContext il contesto del messaggio SOAP
     * @return il codice IPA dell'ente
     */
    private String estraiCodIpaEnteDaRichiesta(MessageContext messageContext) {
        try {
            String soapEnvelope = extractFullSoapEnvelope(messageContext);
            return extractEnteIdentifier(soapEnvelope);
        } catch (Exception e) {
            log.warn("Impossibile estrarre codIpaEnte dal contesto SOAP per il post-processing: {}",
                    e.getMessage());
            return "SCONOSCIUTO";
        }
    }

    /**
     * Determina la modalità di routing per un ente.
     *
     * @param codIpaEnte il codice IPA dell'ente
     * @return la modalità di routing (PU o LEGACY)
     */
    private ModalitaRouting determinaModalitaRouting(String codIpaEnte) {
        try {
            return enteCacheService.findByCodIpaEnte(codIpaEnte)
                    .map(ente -> ente.isPiattaformaUnitaria()
                            ? ModalitaRouting.PIATTAFORMA_UNITARIA
                            : ModalitaRouting.LEGACY)
                    .orElse(ModalitaRouting.LEGACY);
        } catch (Exception e) {
            log.warn("Impossibile determinare la modalità di routing per ente '{}': {}. "
                    + "Default a LEGACY.", codIpaEnte, e.getMessage());
            return ModalitaRouting.LEGACY;
        }
    }

    /**
     * Estrae il contenuto testuale di un tag XML dalla risposta (ricerca per nome locale).
     *
     * @param element  l'elemento XML in cui cercare
     * @param tagName  il nome locale del tag
     * @return il contenuto testuale, o {@code null} se non trovato
     */
    private String estraiTestoTag(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String value = nodes.item(0).getTextContent().trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    /**
     * Sostituisce il contenuto testuale di un tag XML nella risposta.
     *
     * @param element   l'elemento XML in cui cercare
     * @param tagName   il nome locale del tag
     * @param nuovoValore il nuovo valore testuale
     */
    private void sostituisciTestoTag(Element element, String tagName, String nuovoValore) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            nodes.item(0).setTextContent(nuovoValore);
        }
    }

    // --- 3-16: tutti gli altri metodi restano INVARIATI ---
    // (paaSILChiediEsitoCarrelloDovuti, paaSILChiediPagati, etc.)
    // ... codice identico a prima ...

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }

    @Override
    public String getFaultDetailNamespace() {
        return Constants.NS_FAULT_MYPAY;
    }
}
```

**NOTA IMPORTANTE**: Il metodo `estraiCodIpaEnteDaRichiesta` richiama `extractFullSoapEnvelope` e `extractEnteIdentifier` che sono già stati eseguiti in `processRequest`. Questo comporta una doppia estrazione. Un'alternativa più efficiente sarebbe:
- Memorizzare il `codIpaEnte` e la `RoutingDecision` in un `ThreadLocal` durante `processRequest`
- Oppure creare un overload di `processRequest` che restituisca anche il `codIpaEnte` e la `RoutingDecision`

Per la prima implementazione, la doppia estrazione è accettabile (il costo è trascurabile). Si può ottimizzare in seguito.

### 5. UploadForwardingClient — Client HTTP per inoltro file

**File NUOVO**: `api/client/UploadForwardingClient.java`

```java
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
import org.springframework.http.*;
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
            // Buffer size elevato per file grandi
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
     * farebbe direttamente verso MyBoxController.uploadByWS del backend legacy.
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
        log.info("Inoltro file upload al backend legacy: {} (file: {}, dimensione: {} byte)",
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
        log.info("Inoltro file upload alla Piattaforma Unitaria: {} (ente: '{}', file: {}, dimensione: {} byte)",
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
            // Se è un 401, prova con un token refreshato
            if (e.getHttpStatus() != null && e.getHttpStatus() == 401) {
                log.warn("Ricevuto 401 dalla PU per upload. Retry con token refreshato per ente '{}'",
                        codIpaEnte);
                String nuovoToken = oAuthTokenService.refreshToken(
                        codIpaEnte, ente.getClientId(), ente.getClientSecret());
                headers.setBearerAuth(nuovoToken);
                HttpEntity<MultiValueMap<String, Object>> retryRequest = new HttpEntity<>(body, headers);
                return eseguiRichiestaUpload(uploadUrl, retryRequest, "PU (retry)");
            }
            throw e;
        }
    }

    /**
     * Costruisce il body multipart per la richiesta di upload.
     *
     * <p>Il formato replica quello atteso da MyBoxController.uploadByWS:
     * <ul>
     *   <li>{@code authorizationToken}: parametro stringa con il JWT</li>
     *   <li>{@code file}: il file binario</li>
     * </ul>
     *
     * @param authorizationToken il JWT di autorizzazione
     * @param file               il file da caricare
     * @return il body multipart
     */
    private MultiValueMap<String, Object> costruisciBodyMultipart(String authorizationToken,
                                                                   MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("authorizationToken", authorizationToken);

        try {
            // Usa ByteArrayResource con override di getFilename() per mantenere il nome file
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            // Aggiunge il file con gli header Content-Disposition corretti
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);
            body.add("file", filePart);

        } catch (IOException e) {
            throw new PiattaformaCommunicationException(
                    "Errore nella lettura del file per l'inoltro dell'upload", e);
        }

        return body;
    }

    /**
     * Esegue la richiesta HTTP POST multipart di upload.
     */
    private String eseguiRichiestaUpload(String url,
                                          HttpEntity<MultiValueMap<String, Object>> request,
                                          String destinazione) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Risposta ricevuta dall'upload verso {}: Status {}", destinazione, response.getStatusCode());
            log.debug("Corpo risposta upload: {}", response.getBody());
            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.error("Errore HTTP nell'upload verso {}: {} - {}",
                    destinazione, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PiattaformaCommunicationException(
                    "Errore HTTP nell'upload verso " + destinazione,
                    e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            log.error("Timeout o errore di rete nell'upload verso {}: {}",
                    destinazione, e.getMessage());
            throw new PiattaformaCommunicationException(
                    "Timeout o errore di rete nell'upload verso " + destinazione, e);

        } catch (RestClientException e) {
            log.error("Errore nella comunicazione per l'upload verso {}: {}",
                    destinazione, e.getMessage(), e);
            throw new PiattaformaCommunicationException(
                    "Errore nella comunicazione per l'upload verso " + destinazione, e);
        }
    }

    /**
     * Fallback per upload verso backend legacy (circuit breaker aperto).
     */
    public String forwardUploadFallback(String uploadUrl, String authorizationToken,
                                         MultipartFile file, Throwable ex) {
        log.error("Circuit breaker aperto per upload verso backend legacy. Causa: {}", ex.getMessage());
        throw new PiattaformaCommunicationException(
                "Backend legacy temporaneamente non raggiungibile per l'upload (circuit breaker aperto).", 503);
    }

    /**
     * Fallback per upload verso PU (circuit breaker aperto).
     */
    public String forwardUploadPuFallback(String uploadUrl, String authorizationToken,
                                           MultipartFile file, String codIpaEnte, Throwable ex) {
        log.error("Circuit breaker aperto per upload verso PU per ente '{}'. Causa: {}",
                codIpaEnte, ex.getMessage());
        throw new PiattaformaCommunicationException(
                "Piattaforma Unitaria temporaneamente non raggiungibile per l'upload (circuit breaker aperto).", 503);
    }
}
```

**NOTA**: La classe `PiattaformaCommunicationException` deve avere un metodo `getHttpStatus()`. Verificare se esiste. Se non c'è, bisogna aggiungerlo o usare un approccio diverso per il retry su 401.

### 6. UploadFlussoController — Endpoint REST per upload dal SIL

**File NUOVO**: `api/upload/UploadFlussoController.java`

```java
package it.ariaspa.mypay.mypaycore.api.upload;

import it.ariaspa.mypay.mypaycore.api.client.UploadForwardingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoint REST per il proxy upload dei flussi di import.
 *
 * <p>Questo controller riceve i file di upload dai SIL e li inoltra
 * al backend corretto (legacy o PU) utilizzando l'URL originale
 * salvata nella cache durante il post-processing di
 * {@code paaSILAutorizzaImportFlusso}.
 *
 * <p>Il SIL chiama questo endpoint nella stessa modalità in cui
 * chiamerebbe l'endpoint {@code MyBoxController.uploadByWS} del backend:
 * un POST multipart con {@code authorizationToken} come parametro
 * e il file nel body.
 *
 * <p>Path: {@code POST /api/upload/flusso}
 *
 * @see UploadProxyCacheService
 * @see UploadForwardingClient
 */
@RestController
public class UploadFlussoController {

    private static final Logger log = LoggerFactory.getLogger(UploadFlussoController.class);

    private final UploadProxyCacheService uploadProxyCacheService;
    private final UploadForwardingClient uploadForwardingClient;

    public UploadFlussoController(UploadProxyCacheService uploadProxyCacheService,
                                  UploadForwardingClient uploadForwardingClient) {
        this.uploadProxyCacheService = uploadProxyCacheService;
        this.uploadForwardingClient = uploadForwardingClient;
    }

    /**
     * Riceve il file di upload dal SIL e lo inoltra al backend corretto.
     *
     * <p>Flusso:
     * <ol>
     *   <li>Estrae il file multipart dalla richiesta</li>
     *   <li>Recupera l'entry dalla cache usando l'authorizationToken</li>
     *   <li>Inoltra il file al backend (legacy o PU)</li>
     *   <li>Restituisce la risposta del backend al SIL</li>
     * </ol>
     *
     * @param authorizationToken token JWT di autorizzazione (generato dal backend)
     * @param request            la richiesta multipart contenente il file
     * @return la risposta del backend (formato JSON identico a MyBoxController.uploadByWS)
     */
    @PostMapping(path = "/api/upload/flusso", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFlusso(
            @RequestParam("authorizationToken") String authorizationToken,
            MultipartHttpServletRequest request) {

        log.info("Ricevuta richiesta di upload flusso dal SIL");

        // Estrai il file dalla richiesta multipart
        MultipartFile file = estraiFile(request);
        if (file == null || file.isEmpty()) {
            log.warn("Richiesta di upload senza file allegato");
            return rispostaErrore("400", "Nessun file allegato nella richiesta di upload");
        }

        log.info("File ricevuto: nome='{}', dimensione={} byte, tipo='{}'",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        // Recupera l'entry dalla cache (one-shot: viene rimossa dopo il recupero)
        Optional<UploadProxyEntry> entryOpt = uploadProxyCacheService.recuperaERimuovi(authorizationToken);
        if (entryOpt.isEmpty()) {
            log.warn("Nessuna entry trovata nella cache per l'authorizationToken fornito. "
                    + "Potrebbe essere scaduto o già utilizzato.");
            return rispostaErrore("400", "Token di autorizzazione non valido, scaduto o già utilizzato");
        }

        UploadProxyEntry entry = entryOpt.get();
        log.info("Entry recuperata dalla cache: ente='{}', routing={}, requestToken='{}'",
                entry.getCodIpaEnte(), entry.getModalitaRouting(), entry.getRequestToken());

        // Inoltra il file al backend corretto
        try {
            String rispostaBackend;
            if (entry.isPiattaformaUnitaria()) {
                rispostaBackend = uploadForwardingClient.inoltraAllaPU(
                        entry.getUploadUrlOriginale(),
                        authorizationToken,
                        file,
                        entry.getCodIpaEnte());
            } else {
                rispostaBackend = uploadForwardingClient.inoltraAlLegacy(
                        entry.getUploadUrlOriginale(),
                        authorizationToken,
                        file);
            }

            log.info("Upload completato con successo per ente '{}', requestToken '{}'",
                    entry.getCodIpaEnte(), entry.getRequestToken());

            // Restituisci la risposta del backend direttamente al SIL
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(rispostaBackend);

        } catch (Exception e) {
            log.error("Errore nell'inoltro del file per ente '{}', requestToken '{}': {}",
                    entry.getCodIpaEnte(), entry.getRequestToken(), e.getMessage(), e);
            return rispostaErrore("500", "Errore nell'inoltro del file al backend: " + e.getMessage());
        }
    }

    /**
     * Estrae il file multipart dalla richiesta HTTP.
     *
     * <p>Cerca il primo file presente nella richiesta multipart,
     * indipendentemente dal nome del parametro.
     *
     * @param request la richiesta multipart
     * @return il file multipart, o {@code null} se non trovato
     */
    private MultipartFile estraiFile(MultipartHttpServletRequest request) {
        var fileMap = request.getFileMap();
        if (fileMap.isEmpty()) {
            return null;
        }
        // Prende il primo file presente nella richiesta
        return fileMap.values().iterator().next();
    }

    /**
     * Costruisce una risposta di errore nel formato atteso dal SIL.
     *
     * <p>Il formato replica quello di MyBoxController.uploadByWS:
     * una lista con un oggetto contenente {@code codice} e {@code descrizione}.
     *
     * @param codice      il codice di errore ("400" o "500")
     * @param descrizione la descrizione dell'errore
     * @return la risposta HTTP con il body di errore
     */
    private ResponseEntity<List<Map<String, String>>> rispostaErrore(String codice, String descrizione) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("codice", codice);
        errorMap.put("descrizione", descrizione);
        return ResponseEntity.status(HttpStatus.OK).body(List.of(errorMap));
    }
}
```

### 7. Configurazione Properties

#### `application.properties` (base) — aggiungere:

```properties
# --- Upload Proxy ---
# URL base del middleware esposto ai SIL (senza trailing slash).
# Usata per costruire la uploadUrl sostitutiva nella risposta di paaSILAutorizzaImportFlusso.
middleware.upload.proxy.base-url=${MIDDLEWARE_UPLOAD_BASE_URL:http://localhost:8086}
# TTL della cache upload proxy in secondi (default: 1 ora)
middleware.upload.proxy.cache-ttl-seconds=3600
# Timeout per l'inoltro dei file di upload (più elevati per file grandi)
middleware.upload.proxy.connect-timeout-ms=10000
middleware.upload.proxy.read-timeout-ms=120000
# Intervallo di pulizia cache in millisecondi (default: 5 minuti)
middleware.upload.proxy.cleanup-interval-ms=300000
# Dimensione massima file upload
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

#### `application-dev.properties` — aggiungere:

```properties
# --- Upload Proxy (dev) ---
middleware.upload.proxy.base-url=${MIDDLEWARE_UPLOAD_BASE_URL:http://localhost:8086}
middleware.upload.proxy.cache-ttl-seconds=600
# Accesso anonimo anche sul path /api/upload/**
spl.security.authentication.anonymous.uri-matchers=/**
```

### 8. Abilitare @EnableScheduling

Verificare se `@EnableScheduling` è presente in `Application.java` o in una classe di configurazione. Se non c'è, aggiungere:

```java
@EnableScheduling
@SpringBootApplication
public class Application {
    ...
}
```

### 9. Verifica PiattaformaCommunicationException

Verificare che la classe `PiattaformaCommunicationException` abbia un campo `httpStatus` con getter. Dall'analisi del codice nel `PiattaformaUnitariaClient`, il costruttore accetta `(String, int, Exception)` dove `int` è lo status HTTP. Bisogna assicurarsi che ci sia un metodo `getHttpStatus()` per il retry 401 in `UploadForwardingClient`.

---

## Punti Aperti / Ottimizzazioni Future

1. **Doppia estrazione codIpaEnte**: Nel post-processing si ri-estrae il codIpaEnte dal MessageContext. In futuro si potrebbe passare come parametro attraverso un overload di processRequest o un ThreadLocal.

2. **Test unitari**: Dopo l'implementazione, creare:
   - Test per `UploadProxyCacheService` (salva, recupera, pulizia TTL)
   - Test per `UploadFlussoController` (mock del client)
   - Test per `UploadForwardingClient` (WireMock)
   - Test per il post-processing nel `PagamentiTelematiciDovutiPagatiEndpoint`

3. **Metriche**: Aggiungere contatori Micrometer per upload riusciti/falliti.

4. **Log transazionale**: Aggiungere logging del flusso upload nella tabella `mygov_mw_transaction_log`.

5. **Coesistenza Spring WS + Spring MVC**: Il `MessageDispatcherServlet` è mappato su `/ws/*`. L'endpoint REST è su `/api/upload/flusso`. Non dovrebbero esserci conflitti di path. Verificare al primo avvio.

---

## Ordine di Implementazione

1. POM (aggiungere `spring-boot-starter-web`)
2. `UploadProxyEntry.java` (DTO)
3. `UploadProxyCacheService.java` (cache)
4. `UploadForwardingClient.java` (client)
5. `UploadFlussoController.java` (controller)
6. Modificare `PagamentiTelematiciDovutiPagatiEndpoint.java` (post-processing)
7. Properties (configurazione)
8. `@EnableScheduling` (se necessario)
9. Verificare `PiattaformaCommunicationException.getHttpStatus()`
10. Compilare e testare
