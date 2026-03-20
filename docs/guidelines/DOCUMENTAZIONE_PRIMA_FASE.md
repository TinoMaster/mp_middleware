# DOCUMENTAZIONE_PRIMA_FASE
## Middleware MyPay — Guida Tecnica Completa

**Versione**: 2.0.0  
**Data**: 20 Marzo 2026  
**Stato**: Fasi 1-7 e 9 completate — Routing completo, log transazionale, metriche, 124 test | Fase 8 bloccata (censimento endpoint)

---

## Indice

1. [Cos'è questo progetto](#1-cosè-questo-progetto)
2. [Architettura generale](#2-architettura-generale)
3. [Struttura del progetto Maven](#3-struttura-del-progetto-maven)
4. [Struttura dei pacchetti Java](#4-struttura-dei-pacchetti-java)
5. [Modulo di Configurazione](#5-modulo-di-configurazione)
6. [Modulo di Autenticazione](#6-modulo-di-autenticazione)
7. [Modulo Client Piattaforma](#7-modulo-client-piattaforma)
8. [Modulo Persistenza (domain + repository)](#8-modulo-persistenza-domain--repository)
9. [Modulo Routing](#9-modulo-routing)
10. [Modulo Endpoint SOAP](#10-modulo-endpoint-soap)
11. [Gestione degli Errori](#11-gestione-degli-errori)
12. [Monitoraggio e Health Check](#12-monitoraggio-e-health-check)
13. [Resilienza](#13-resilienza)
14. [Ambienti e Profili](#14-ambienti-e-profili)
15. [Test Unitari](#15-test-unitari)
16. [Come avviare il progetto](#16-come-avviare-il-progetto)
17. [Come testare il flusso completo](#17-come-testare-il-flusso-completo)
18. [Cosa NON è ancora implementato](#18-cosa-non-è-ancora-implementato)
19. [Prossimi passi — Fasi Future](#19-prossimi-passi--fasi-future)
20. [Agenti OpenCode](#20-agenti-opencode)

---

## 1. Cos'è questo progetto

`mypay.mypaycore` è un **middleware di integrazione** sviluppato con il framework **SpringLine2** di ARIA S.p.A. (Azienda Regionale per l'Innovazione e gli Acquisti, Regione Lombardia).

### Problema che risolve

I **SIL** (Sistemi Informativi Locali) degli enti pubblici (comuni, province, ecc.) devono comunicare con la **Piattaforma Unitaria** di pagoPA per gestire pagamenti, riconciliazioni e flussi di tesoreria. La Piattaforma Unitaria richiede autenticazione OAuth2, che i SIL non sono in grado di gestire autonomamente.

Il middleware si interpone tra i due sistemi e:
- Espone un'interfaccia **SOAP** semplice ai SIL (protocollo già noto ai sistemi legacy)
- Gestisce in autonomia l'**autenticazione OAuth2** verso pagoPA
- **Inoltra** le richieste autenticate alla Piattaforma Unitaria
- **Restituisce** le risposte ai SIL nel formato atteso

### Flusso ad alto livello

```
Ente Pubblico (SIL)
       │
       │  SOAP Request
       │  POST /ws/pivot/PagamentiTelematiciPagatiRiconciliati
       │       (oppure /ws/pa/..., /ws/fesp/...)
       │  Content-Type: text/xml
       │  Body: <soapenv:Envelope>
       │          <Header><codIpaEnte>...</codIpaEnte></Header>
       │          <Body><password>...</password></Body>
       │        </soapenv:Envelope>
       ▼
┌──────────────────────────────────────────────────┐
│           MIDDLEWARE (questo progetto)            │
│                                                  │
│  1. Riceve la richiesta SOAP                     │
│  2. Estrae l'Envelope completo                   │
│     (Header + Body)                              │
│  3. [Fase 7] Determina la modalità di routing    │
│     consultando il DB (per ente + operazione)    │
│  4a. Modalità PU: ottiene token OAuth2,          │
│      inoltra con Bearer token                    │
│  4b. Modalità LEGACY: forward diretto            │
│      al backend (senza OAuth2)                   │
│  5. Riceve la risposta                           │
│  6. Estrae il body dalla risposta                │
│  7. Restituisce la risposta al SIL               │
└──────────────────────────────────────────────────┘
       │                           │
       │  Modalità PU              │  Modalità LEGACY
       │  POST /pu/sil/soap/...   │  POST /ws/pivot/...
       │  Authorization: Bearer    │  (nessun token aggiunto)
       ▼                           ▼
 Piattaforma Unitaria        Backend Legacy
     (pagoPA)              (mypay o mypivot)
```

> **NOTA IMPORTANTE**: Il SIL **NON** invia un JWT/Bearer token. L'autenticazione del SIL
> avviene tramite `codIpaEnte` (nell'Header SOAP) e `password` (nel Body SOAP).
> Il middleware gestisce internamente l'autenticazione OAuth2 verso la Piattaforma Unitaria.
> Il routing PU vs LEGACY è stato implementato nella Fase 7 tramite il `RoutingDecisionService`.
> Ogni richiesta viene instradata dinamicamente in base alla configurazione dell'ente nel database.

---

## 2. Architettura generale

### Framework base: SpringLine2

Il progetto è costruito su **SpringLine2** (versione 2027.01.01), un framework proprietario ARIA che estende Spring Boot 3.5.5 e fornisce:

| Componente SpringLine2 | Funzione |
|------------------------|----------|
| `springline2-core` | Nucleo: sicurezza, logging MON/APP, configurazione cloud |
| `springline2-ws` | Client SOAP (lato client verso sistemi esterni) |
| `springline2-openapi` | Integrazione Swagger/OpenAPI automatica |

### Dipendenze aggiuntive (non SpringLine2)

| Dipendenza | Versione | Scopo |
|-----------|---------|-------|
| `spring-boot-starter-web-services` | gestita da Spring Boot | Server SOAP (riceve richieste dai SIL) |
| `jakarta.xml.bind-api` | gestita da Spring Boot | Marshalling/unmarshalling XML |
| `jaxb-runtime` | gestita da Spring Boot | Implementazione runtime JAXB |
| `resilience4j-spring-boot3` | 2.2.0 | Circuit Breaker e Retry |
| `spring-boot-starter-aop` | gestita da Spring Boot | Necessario per le annotazioni Resilience4j |
| `spring-boot-starter-actuator` | gestita da Spring Boot | Health check e metriche |
| `spring-boot-starter-jdbc` | gestita da Spring Boot | Supporto JDBC e transaction manager Spring |
| `jdbi3-spring5` | 3.27.0 | Integrazione Jdbi con contesto Spring |
| `jdbi3-sqlobject` | 3.27.0 | DAO dichiarativi basati su interfacce e annotazioni SQL |
| `jdbi3-stringtemplate4` | 3.27.0 | Template SQL dinamici per query Jdbi |
| `postgresql` | gestita da Spring Boot | Driver JDBC PostgreSQL |
| `spring-boot-starter-test` | gestita da Spring Boot | Test unitari (JUnit 5, Mockito) |
| `spring-ws-test` | gestita da Spring Boot | Utility di test per SOAP |

### Moduli Maven del progetto

```
mypay.mypaycore/                    ← POM padre (aggregator)
├── mypay.mypaycore-springboot/     ← Applicazione Spring Boot (questo è il cuore)
├── mypay.mypaycore-db/             ← Script SQL PostgreSQL (3 script: tabelle ente_config, transaction_log, dati esempio)
├── mypay.mypaycore-properties/     ← Modulo proprietà (SpringLine2 config)
└── mypay.mypaycore-release/        ← Modulo di packaging per il rilascio
```

---

## 3. Struttura del progetto Maven

### `mypay.mypaycore-springboot` (modulo principale)

Contiene tutto il codice sorgente Java dell'applicazione.

```
mypay.mypaycore-springboot/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/it/ariaspa/mypay/mypaycore/api/
    │   │   ├── Application.java
    │   │   ├── auth/
    │   │   ├── client/
    │   │   ├── common/
    │   │   ├── config/
    │   │   ├── domain/
    │   │   ├── health/
    │   │   ├── logging/
    │   │   ├── metrics/
    │   │   ├── repository/
    │   │   ├── routing/
    │   │   └── soap/
    │   └── resources/config/
    │       ├── application.properties        ← configurazione base (comune a tutti i profili)
    │       ├── application-dev.properties    ← profilo sviluppo (unico profilo attivo)
    │       └── bootstrap.properties
    └── test/
        ├── java/.../api/
        │   ├── auth/OAuthTokenServiceTest.java
        │   ├── client/
        │   │   ├── PiattaformaUnitariaClientTest.java
        │   │   └── ProxyForwardingClientTest.java
        │   ├── config/
        │   │   ├── PathRegistryConfigTest.java
        │   │   └── BackendRoutingConfigTest.java
        │   ├── domain/DomainModelTest.java
        │   ├── health/EnteConfigHealthIndicatorTest.java
        │   ├── logging/TransactionLoggingServiceTest.java
        │   ├── metrics/MiddlewareMetricsServiceTest.java
        │   ├── repository/
        │   │   ├── EnteConfigRowMapperTest.java
        │   │   └── EnteConfigCacheServiceTest.java
        │   ├── routing/RoutingDecisionServiceTest.java
        │   └── soap/
        │       ├── endpoint/ReconciliationEndpointTest.java
        │       └── exception/SoapFaultExceptionResolverTest.java
        └── resources/config/application.properties
```

### `mypay.mypaycore-db` (modulo database)

Contiene gli script SQL PostgreSQL per la creazione delle tabelle del middleware:

| Script | Scopo |
|--------|-------|
| `001_CREATE_MWPAY_ENTE_CONFIG.sql` | Tabella configurazione routing per ente e operazione |
| `002_CREATE_MWPAY_TRANSACTION_LOG.sql` | Tabella log transazionale delle richieste SOAP |
| `003_INSERT_ENTE_CONFIG_EXAMPLE.sql` | Dati di esempio per l'ambiente di sviluppo |

Il modulo è gestito dal plugin `custom-package-plugin` (ARIA) che produce un archivio ZIP con gli script per il deployment.

> **Nota**: Il tag `<summary>` è stato rimosso dalla configurazione del plugin perché non riconosciuto dalla versione `3.2.0` (causava un falso positivo in IntelliJ pur non compromettendo la build).

### `mypay.mypaycore-properties` (modulo di configurazione per il deployment)

Contiene i file di configurazione utilizzati durante il deployment su server. I file sono in formato **`.properties`**:

```
mypay.mypaycore-properties/
└── src/main/resources/
    ├── application.properties   ← template di configurazione per il deployment (DataSource, SSL, SpringLine2)
    ├── bootstrap.properties     ← versione applicazione e configurazioni di bootstrap
    └── startup.sh               ← script di avvio con --spring.profiles.active=dev
```

Il file `application.properties` in questo modulo è il **template di deployment**: contiene placeholder (`<INSERIRE ...>`) per tutte le proprietà sensibili (credenziali DB, password SSL, segreto JWT). Va completato prima del deploy su ogni ambiente.

---

## 4. Struttura dei pacchetti Java

Tutti i sorgenti si trovano sotto:
`it.ariaspa.mypay.mypaycore.api`

```
api/
├── Application.java                         ← Entry point Spring Boot
│
├── config/                                  ← Configurazione applicazione
│   ├── PiattaformaUnitariaConfig.java       (OAuth2 e URL Piattaforma Unitaria)
│   ├── SoapWebServiceConfig.java            (@EnableWs + servlet su /ws/*)
│   ├── PathRegistryConfig.java              (mapping path-prefix → backend — Fase 5)
│   ├── BackendRoutingConfig.java            (URL backend mypay/mypivot — Fase 5)
│   ├── DataSourceConfiguration.java         (DataSource PostgreSQL con HikariCP)
│   └── JdbiConfiguration.java              (istanza Jdbi primaria)
│
├── auth/                                    ← Autenticazione OAuth2
│   ├── OAuthTokenService.java
│   ├── OAuthTokenInterceptor.java
│   └── dto/
│       └── OAuthTokenResponse.java
│
├── routing/                                 ← Logica di routing (Fase 7)
│   ├── RoutingDecision.java                 (risultato immutabile della decisione)
│   └── RoutingDecisionService.java          (cervello del routing: path + DB → decisione)
│
├── client/                                  ← Client HTTP
│   ├── PiattaformaUnitariaClient.java       (verso PU con OAuth2)
│   └── ProxyForwardingClient.java           (forward legacy senza OAuth2 — Fase 5)
│
├── soap/                                    ← Endpoint SOAP lato SIL
│   ├── endpoint/
│   │   └── ReconciliationEndpoint.java      (con routing dinamico, logging e metriche)
│   └── exception/
│       └── SoapFaultExceptionResolver.java  (5 tipi di eccezione mappati)
│
├── domain/                                  ← Modelli dati (Fase 6)
│   ├── ModalitaRouting.java                 (enum: PIATTAFORMA_UNITARIA, LEGACY)
│   ├── EnteConfig.java                      (modello tabella mwpay_ente_config)
│   └── TransactionLog.java                  (modello tabella mwpay_transaction_log)
│
├── repository/                              ← Accesso dati Jdbi (Fase 6)
│   ├── EnteConfigRepository.java            (DAO Jdbi — SqlObject)
│   ├── EnteConfigRowMapper.java
│   ├── EnteConfigCacheService.java          (cache TTL in-memory — ConcurrentHashMap)
│   ├── TransactionLogRepository.java        (DAO Jdbi — SqlObject)
│   └── TransactionLogRowMapper.java
│
├── logging/                                 ← Log transazionale (Fase 9)
│   └── TransactionLoggingService.java       (log su DB con resilienza — mai blocca il SIL)
│
├── metrics/                                 ← Metriche Micrometer (Fase 9)
│   └── MiddlewareMetricsService.java        (Counter, Timer, Gauge per Actuator)
│
├── common/                                  ← Classi condivise
│   └── exception/
│       ├── PiattaformaAuthenticationException.java
│       ├── PiattaformaCommunicationException.java
│       ├── EnteNonCensitoException.java     (Fase 7)
│       └── PathNonRiconosciutoException.java (Fase 7)
│
├── health/                                  ← Health check Actuator
│   ├── OAuthTokenHealthIndicator.java
│   ├── PiattaformaUnitariaHealthIndicator.java
│   └── EnteConfigHealthIndicator.java       (Fase 9 — verifica enti configurati)
```

---

## 5. Modulo di Configurazione

### `PiattaformaUnitariaConfig.java`

**Tipo**: `@Configuration` + `@ConfigurationProperties(prefix = "piattaforma-unitaria")`  
**Scopo**: Centralizza tutti i parametri di connessione verso la Piattaforma Unitaria.

Questa classe legge automaticamente dal file `application.properties` il blocco:

```properties
piattaforma-unitaria.base-url=https://api.uat.p4pa.pagopa.it
piattaforma-unitaria.auth.token-url=${piattaforma-unitaria.base-url}/pu/auth/oauth/token
piattaforma-unitaria.auth.client-id=${PIATTAFORMA_CLIENT_ID:SELC_99999000013SIL_RegLomb2}
piattaforma-unitaria.auth.client-secret=${PIATTAFORMA_CLIENT_SECRET:xxxxx}
piattaforma-unitaria.auth.grant-type=client_credentials
piattaforma-unitaria.auth.scope=openid
```

**Proprietà esposte**:

| Proprietà | Tipo | Descrizione |
|-----------|------|-------------|
| `baseUrl` | `String` | URL base della Piattaforma Unitaria |
| `auth.tokenUrl` | `String` | Endpoint OAuth2 per il token |
| `auth.clientId` | `String` | Identificativo client per OAuth2 |
| `auth.clientSecret` | `String` | Segreto client (da env var in prod) |
| `auth.grantType` | `String` | Sempre `client_credentials` |
| `auth.scope` | `String` | Sempre `openid` |

**Sicurezza**: In produzione, `clientId` e `clientSecret` vengono iniettati tramite variabili d'ambiente (`${PIATTAFORMA_CLIENT_ID}` e `${PIATTAFORMA_CLIENT_SECRET}`), mai hardcoded.

---

### `SoapWebServiceConfig.java`

**Tipo**: `@Configuration` + `@EnableWs` + `implements WsConfigurer`  
**Scopo**: Configura Spring WS per esporre endpoint SOAP in ricezione dai SIL.

**Cosa fa**:
- Registra un `MessageDispatcherServlet` mappato su `/ws/*`
- Questo servlet intercetta tutte le richieste HTTP POST con Content-Type `text/xml` verso i path dei backend (`/ws/pivot/*`, `/ws/pa/*`, `/ws/fesp/*`)
- I singoli endpoint SOAP (annotati con `@Endpoint`) vengono rilevati automaticamente

**Nota tecnica importante**: `springline2-ws` è una libreria client SOAP (per chiamare servizi esterni). Per esporre endpoint SOAP server-side è necessaria la dipendenza separata `spring-boot-starter-web-services`, che è stata aggiunta esplicitamente al pom.

---

### `PathRegistryConfig.java` (Fase 5)

**Tipo**: `@Configuration` + `@ConfigurationProperties(prefix = "routing")`  
**Scopo**: Registro configurabile dei path-prefix e il loro mapping verso i backend (mypay o mypivot).

**Funzionamento**:
- Legge la mappa `routing.path-map.*` da `application.properties`
- Converte le chiavi normalizzate in path reali (`ws-pivot` → `/ws/pivot`)
- Metodo `resolveBackend(String requestPath)` → `Optional<BackendDestinatario>` con longest-prefix matching
- Enum interno `BackendDestinatario` (`MYPAY`, `MYPIVOT`)
- Validazione `@PostConstruct`: fallisce se il path-map è vuoto

**Proprietà**:
```properties
routing.path-map.ws-pivot=MYPIVOT
routing.path-map.ws-pa=MYPAY
routing.path-map.ws-fesp=MYPAY
```

---

### `BackendRoutingConfig.java` (Fase 5)

**Tipo**: `@Configuration` + `@ConfigurationProperties(prefix = "backend")`  
**Scopo**: Centralizza gli URL dei backend legacy (mypay e mypivot).

**Proprietà**:
```properties
backend.mypivot.base-url=${BACKEND_MYPIVOT_URL:http://localhost:8081}
backend.mypay.base-url=${BACKEND_MYPAY_URL:http://localhost:8082}
```

**Metodo pubblico**:
- `getBaseUrlFor(BackendDestinatario backend)` → `String` — restituisce l'URL base in base al tipo di backend

---

### `DataSourceConfiguration.java`

**Tipo**: `@Configuration`  
**Scopo**: Configura il DataSource PostgreSQL `pa` (database principale di MyPay) con pool HikariCP per l'accesso JDBC/Jdbi.

**Perché è necessaria**: Spring Boot auto-configura il datasource solo se le proprietà seguono il prefisso standard `spring.datasource.*`. Il progetto usa il prefisso personalizzato `spring.datasource.pa.*` (convenzione ereditata dal progetto mypay4 legacy), quindi è necessaria una classe di configurazione esplicita.

**Bean creati**:

| Bean | Tipo | Descrizione |
|------|------|-------------|
| `paDataSourceProperties` | `DataSourceProperties` | Legge `spring.datasource.pa.*` (url, username, password, driver) |
| `dsPa` | `DataSource`/`HikariDataSource` | Pool di connessioni; parametri da `spring.datasource.pa.hikari.*` |
| `tmPa` | `DataSourceTransactionManager` | Gestione transazioni JDBC condivisa con Jdbi |

Tutti i bean sono annotati con `@Primary` poiché e' il datasource unico dell'applicazione.

**Dettagli rilevanti**:
- supporta password cifrata tramite `spring.datasource.cryptPassword=true` e decrypt Jasypt
- forza `autoCommit=false` sul datasource per demandare il controllo transazionale a Spring
- espone il datasource con nome `dsPa`, riutilizzato dalla configurazione Jdbi

**Configurazione Properties corrispondente** (esempio profilo `dev`):
```properties
spring.datasource.pa.driver-class-name=org.postgresql.Driver
spring.datasource.pa.url=${DB_PA_URL:jdbc:postgresql://localhost:5432/mypay_local_copy}
spring.datasource.pa.username=${DB_PA_USERNAME:admin}
spring.datasource.pa.password=${DB_PA_PASSWORD:admin}
spring.datasource.pa.hikari.minimum-idle=1
spring.datasource.pa.hikari.maximum-pool-size=5
spring.datasource.pa.hikari.pool-name=HikariPool-PA-dev
```

---

### `JdbiConfiguration.java`

**Tipo**: `@Configuration`  
**Scopo**: Completa il layer di persistenza configurando l'istanza Jdbi primaria collegata al datasource `dsPa`.

**Cosa fa**:
- crea il bean `jdbiPa` usato dai componenti di accesso dati
- avvolge il datasource in `TransactionAwareDataSourceProxy` per partecipare correttamente alle transazioni Spring
- applica timeout globali alle query tramite `SqlStatements`
- abilita il logging SQL tramite `JdbiSqlLogger` quando richiesto dalle proprieta'
- installa automaticamente plugin Jdbi e `RowMapper` presenti nel contesto Spring
- registra `SqlObjectPlugin` per supportare DAO dichiarativi con annotazioni `@SqlQuery`, `@SqlUpdate`, ecc.

**Bean creati**:

| Bean | Tipo | Descrizione |
|------|------|-------------|
| `jdbiPa` | `Jdbi` | Istanza primaria di Jdbi associata al datasource `dsPa` |
| `sqlObjectPlugin` | `JdbiPlugin` | Abilita il modello SQL Object per DAO/interfacce annotate |
| `messageSource` | `ResourceBundleMessageSource` | Message source condiviso per messaggi applicativi |

**Utilizzo consigliato**:
- definire DAO o repository Jdbi invece di entity e repository JPA
- usare `@Transactional` sui servizi Spring quando piu' operazioni Jdbi devono partecipare alla stessa transazione
- registrare `RowMapper` dedicati per il mapping dei result set verso DTO o model applicativi

---

## 6. Modulo di Autenticazione

### `OAuthTokenResponse.java` (DTO)

**Tipo**: POJO con annotazioni Jackson e Lombok  
**Scopo**: Deserializza la risposta JSON dell'endpoint OAuth2 in un oggetto Java.

```json
{
  "access_token": "eyJhbGci...",
  "token_type":   "Bearer",
  "expires_in":   3600
}
```

**Note di sicurezza**: Il campo `accessToken` è escluso dal metodo `toString()` con `@ToString(exclude = "accessToken")` per evitare che il token appaia accidentalmente nei log.

---

### `OAuthTokenService.java`

**Tipo**: `@Service`  
**Scopo**: Gestisce il ciclo di vita completo del token OAuth2.

**Funzionamento dettagliato**:

```
getAccessToken() chiamato
        │
        ▼
   Token valido in cache?
   (cachedToken != null
    && Instant.now() < tokenExpiryTime)
        │
   Sì ──┤──► restituisce cachedToken (nessuna chiamata HTTP)
        │
   No ──┤──► acquisisce ReentrantLock
              (solo un thread alla volta richiede il token)
        │
        ▼
   Double-check: token ancora non valido?
        │
    Sì ──┤──► POST token-url con client_credentials
         │     I parametri OAuth2 vengono inviati come **query string** nell'URL:
         │     POST token-url?client_id=...&client_secret=...&grant_type=client_credentials&scope=openid
         │     NOTA: la PU restituisce 404 se i parametri vengono inviati come body form-urlencoded
        │
        ▼
   Salva token + calcola scadenza
   tokenExpiryTime = now + expires_in - 60 secondi (margine sicurezza)
        │
        ▼
   rilascia lock ──► restituisce token
```

**Parametri tecnici**:
- Connect timeout verso OAuth2: **5 secondi**
- Read timeout verso OAuth2: **10 secondi**
- Margine di sicurezza prima della scadenza: **60 secondi**
- Thread-safety: garantita tramite `ReentrantLock` + `volatile` sulle variabili di cache

**Metodi pubblici**:

| Metodo | Descrizione |
|--------|-------------|
| `getAccessToken()` | Restituisce token valido (dalla cache o nuovo) |
| `refreshToken()` | Forza il refresh del token (usato dopo 401) |
| `invalidateToken()` | Cancella il token dalla cache |
| `isTokenValid()` | Verifica se il token è ancora valido |

---

### `OAuthTokenInterceptor.java`

**Tipo**: `@Component` + `ClientHttpRequestInterceptor`  
**Scopo**: Aggiunge automaticamente l'header `Authorization: Bearer <token>` a ogni richiesta HTTP del `PiattaformaUnitariaClient`.

**Come funziona**: Viene registrato come interceptor nel `RestTemplate` del client. Ad ogni richiesta in uscita verso la Piattaforma Unitaria, chiama `oAuthTokenService.getAccessToken()` e aggiunge l'header prima che la richiesta venga inviata. Il servizio usa la cache, quindi non c'è overhead se il token è ancora valido.

---

## 7. Modulo Client Piattaforma

### `PiattaformaUnitariaClient.java`

**Tipo**: `@Service`  
**Scopo**: Unico punto di accesso al sistema esterno (Piattaforma Unitaria pagoPA).

**Configurazione HTTP**:
- Connect timeout: **5 secondi**
- Read timeout: **30 secondi** (le chiamate SOAP possono essere lente)
- Interceptor automatico: `OAuthTokenInterceptor` (aggiunge Bearer token)

**Flusso di `forwardSoapRequest(path, soapXml)`**:

```
Richiesta SOAP ricevuta
        │
        ▼
 Costruisce URL: baseUrl + path
 Imposta Content-Type: text/xml
        │
        ▼
 POST verso Piattaforma Unitaria
 (OAuthTokenInterceptor aggiunge il token automaticamente)
        │
   ┌────┴────────────────────────────────┐
   │                                      │
  OK                                   Errore
   │                                      │
   ▼                                      ▼
 restituisce                        401 Unauthorized?
 response.getBody()                       │
                                    Sì ───┤──► refreshToken() + retry
                                          │
                                    No ───┤──► lancia eccezione tipizzata
                                               (PiattaformaCommunicationException
                                                o PiattaformaAuthenticationException)
```

**Resilienza applicata** (vedi sezione 11):
- `@CircuitBreaker(name = "piattaformaUnitaria")`: protegge da cascate di errori
- `@Retry(name = "piattaformaUnitaria")`: tentativi con backoff esponenziale
- `forwardSoapRequestFallback()`: risposta di fallback quando il circuit breaker è aperto

---

### `ProxyForwardingClient.java` (Fase 5)

**Tipo**: `@Service`  
**Scopo**: Client HTTP per il forward trasparente verso i backend legacy (mypay/mypivot) in modalità legacy.

**Differenze rispetto a `PiattaformaUnitariaClient`**:
- **Nessun token OAuth2** — le credenziali SIL (`codIpaEnte` + `password`) viaggiano as-is nel payload SOAP
- `RestTemplate` separato, senza `OAuthTokenInterceptor`
- Nessuna trasformazione del payload
- Circuit breaker e retry dedicati (`backendLegacy`)

**Configurazione HTTP**:
- Connect timeout: **5 secondi**
- Read timeout: **30 secondi**
- Content-Type: `text/xml` (SOAP)

**Flusso di `forwardToBackend(String backendBaseUrl, String path, String soapXml)`**:

```
Richiesta SOAP ricevuta (modalità LEGACY)
        │
        ▼
 Costruisce URL: backendBaseUrl + path
 Imposta Content-Type: text/xml
        │
        ▼
 POST verso backend legacy (mypay o mypivot)
 (nessun token OAuth2 aggiunto — forward trasparente)
        │
   ┌────┴────────────────────────────────┐
   │                                      │
  OK                                   Errore
   │                                      │
   ▼                                      ▼
 restituisce                        lancia eccezione tipizzata
 response.getBody()                 (PiattaformaCommunicationException)
```

**Resilienza applicata**:
- `@CircuitBreaker(name = "backendLegacy")`: circuit breaker dedicato (finestra 10, soglia 50%, attesa 30s)
- `@Retry(name = "backendLegacy")`: retry con backoff esponenziale (3 tentativi, 1s → 2s → 4s)
- `forwardToBackendFallback()`: risposta di fallback quando il circuit breaker è aperto

---

## 8. Modulo Persistenza (domain + repository)

Questo modulo gestisce il layer di persistenza del middleware: modelli di dominio, accesso ai dati tramite Jdbi e cache in-memory per le configurazioni degli enti.

### Modelli di dominio (`domain/`)

#### `ModalitaRouting.java` (enum)

**Tipo**: `enum`  
**Scopo**: Rappresenta le due modalità di instradamento di una richiesta SOAP.

| Valore | Descrizione |
|--------|-------------|
| `PIATTAFORMA_UNITARIA` | Inoltro con autenticazione OAuth2 verso la PU di pagoPA — il middleware aggiunge automaticamente il token Bearer |
| `LEGACY` | Forward diretto al backend legacy (mypay o mypivot) — le credenziali SIL (`codIpaEnte` + `password`) viaggiano as-is nel body SOAP |

Il valore viene letto dalla colonna `modalita_routing` della tabella `mwpay_ente_config`.

---

#### `EnteConfig.java` (modello)

**Tipo**: POJO  
**Scopo**: Rappresenta la configurazione di routing per un ente e un tipo di operazione specifico. Corrisponde a un record della tabella `mwpay_ente_config`.

**Campi** (8):

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `id` | `Long` | Identificativo univoco (chiave surrogata auto-generata) |
| `codIpaEnte` | `String` | Codice IPA dell'ente pubblico (es. `"R_LOMBARDIA"`) |
| `tipoOperazione` | `String` | Tipo di operazione SOAP — local part del messaggio (es. `"pivotSILAutorizzaImportFlussoTesoreria"`) |
| `modalitaRouting` | `ModalitaRouting` | Modalità di instradamento: `PIATTAFORMA_UNITARIA` o `LEGACY` |
| `attivo` | `boolean` | Flag di attivazione — consente di disabilitare una regola senza eliminarla |
| `note` | `String` | Note libere (es. motivo della configurazione o ticket di riferimento) |
| `dataCreazione` | `LocalDateTime` | Data e ora di creazione del record |
| `dataAggiornamento` | `LocalDateTime` | Data e ora dell'ultimo aggiornamento |

**Chiave logica**: la coppia `(codIpaEnte, tipoOperazione)`, vincolata da constraint `UNIQUE` a livello di database.

**Costruttori**: vuoto (per Jdbi/Jackson) e completo (tutti gli 8 campi).

---

#### `TransactionLog.java` (modello)

**Tipo**: POJO  
**Scopo**: Rappresenta il log di una singola transazione SOAP processata dal middleware. Corrisponde a un record della tabella `mwpay_transaction_log`.

**Campi** (11):

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `id` | `Long` | Identificativo univoco (chiave surrogata auto-generata) |
| `codIpaEnte` | `String` | Codice IPA dell'ente che ha effettuato la richiesta |
| `tipoOperazione` | `String` | Tipo di operazione SOAP (local part del messaggio) |
| `modalitaRouting` | `ModalitaRouting` | Modalità di instradamento utilizzata (PU o legacy) |
| `destinazione` | `String` | Backend di destinazione: `"MYPAY"` o `"MYPIVOT"` |
| `pathRichiesta` | `String` | Path HTTP della richiesta SOAP ricevuta dal SIL |
| `httpStatusRisposta` | `Integer` | Codice di stato HTTP della risposta dal backend (null se non disponibile) |
| `esito` | `String` | Esito della transazione: `"OK"` o `"ERRORE"` |
| `messaggioErrore` | `String` | Messaggio di errore (solo se esito = ERRORE, senza dati sensibili) |
| `durataMs` | `Long` | Durata della transazione in millisecondi |
| `timestampRichiesta` | `LocalDateTime` | Timestamp della richiesta SOAP ricevuta dal middleware |

**Costruttori**: vuoto (per Jdbi/Jackson) e completo (tutti gli 11 campi).

**Nota**: Il logging è sincrono (post-request) ma non bloccante: se l'inserimento in DB fallisce, viene registrato un warning nel log applicativo senza interrompere la risposta al SIL.

---

### Repository Jdbi (`repository/`)

#### `EnteConfigRepository.java` (DAO Jdbi)

**Tipo**: `interface` con annotazioni Jdbi (`@SqlQuery`, `@SqlUpdate`)  
**Scopo**: Accesso ai dati per la tabella `mwpay_ente_config`. Registrato come bean Spring tramite `JdbiConfiguration`.

**Metodi** (6):

| Metodo | Tipo SQL | Descrizione |
|--------|----------|-------------|
| `findByCodIpaEnteAndTipoOperazione(codIpaEnte, tipoOperazione)` | `@SqlQuery` | Recupera configurazione attiva per ente + operazione — query principale usata dal `RoutingDecisionService` |
| `findAllByCodIpaEnte(codIpaEnte)` | `@SqlQuery` | Tutte le configurazioni attive di un ente, ordinate per `tipo_operazione` |
| `findAllAttive()` | `@SqlQuery` | Tutte le configurazioni attive nel sistema — usata per popolare la cache in-memory |
| `countAttive()` | `@SqlQuery` | Conteggio configurazioni attive — usata dall'health check |
| `insert(codIpaEnte, tipoOperazione, modalitaRouting, note)` | `@SqlUpdate` | Inserisce una nuova regola di routing |
| `updateModalitaRouting(codIpaEnte, tipoOperazione, modalitaRouting)` | `@SqlUpdate` | Aggiorna la modalità di routing e il timestamp `data_aggiornamento` |

**Row mapper**: `EnteConfigRowMapper` (classe dedicata registrata con `@RegisterRowMapper`).

---

#### `TransactionLogRepository.java` (DAO Jdbi)

**Tipo**: `interface` con annotazioni Jdbi  
**Scopo**: Scrittura log transazionale nella tabella `mwpay_transaction_log`. Registrato come bean Spring tramite `JdbiConfiguration`.

**Metodi** (1):

| Metodo | Tipo SQL | Descrizione |
|--------|----------|-------------|
| `insert(codIpaEnte, tipoOperazione, modalitaRouting, destinazione, pathRichiesta, httpStatusRisposta, esito, messaggioErrore, durataMs)` | `@SqlUpdate` | Inserisce un record di log — 9 parametri via `@Bind` |

**Row mapper**: `TransactionLogRowMapper` (registrato con `@RegisterRowMapper`, predisposto per future query di lettura).

**Nota**: Le operazioni di lettura (reporting, diagnostica) potranno essere aggiunte in futuro. Attualmente il focus è sull'inserimento sincrono post-request.

---

### Cache in-memory (`EnteConfigCacheService.java`)

**Tipo**: `@Service`  
**Scopo**: Mantiene una copia in-memory della tabella `mwpay_ente_config` con un TTL configurabile, evitando query al DB a ogni richiesta SOAP.

**Struttura interna**:

| Componente | Tipo | Scopo |
|-----------|------|-------|
| `cache` | `ConcurrentHashMap<String, EnteConfig>` | Mappa thread-safe chiave → configurazione |
| `ultimoCaricamento` | `volatile Instant` | Timestamp dell'ultimo refresh (inizializzato a `Instant.EPOCH`) |
| `refreshLock` | `ReentrantLock` | Garantisce che un solo thread alla volta esegua il refresh |

**Formato chiave**: `"codIpaEnte|tipoOperazione"` (es. `"R_LOMBARDIA|pivotSILAutorizzaImportFlussoTesoreria"`).

**TTL configurabile**:
```properties
middleware.cache.ente-config.ttl-seconds=300   # default: 5 minuti
```

**Ciclo di vita**:

```
@PostConstruct → init()
    → refreshCache() (caricamento iniziale da DB)
        │
        ▼
  findByCodIpaEnteAndTipoOperazione(codIpaEnte, tipoOperazione)
        │
        ▼
  refreshIfExpired()
    → La cache è scaduta?
        │
    No ──┤──► restituisce dalla cache
        │
    Sì ──┤──► tryLock()
              │
         Ottenuto ──┤──► double-check + refreshCache()
              │
         Non ottenuto ──┤──► usa cache corrente (stale-while-revalidate)
```

**Metodi pubblici**:

| Metodo | Descrizione |
|--------|-------------|
| `findByCodIpaEnteAndTipoOperazione(codIpaEnte, tipoOperazione)` | Recupera configurazione dalla cache (con refresh se TTL scaduto) |
| `isEnteCensito(codIpaEnte)` | Verifica se l'ente ha almeno una configurazione attiva |
| `size()` | Numero di entry in cache — usato dal `Gauge` Micrometer e dall'health check |
| `forceRefresh()` | Forza il refresh indipendentemente dal TTL — utile per admin e test |

**Pattern stale-while-revalidate**: Se il refresh fallisce (es. errore DB), la cache corrente viene mantenuta e l'errore viene registrato nel log applicativo. Questo garantisce che il middleware continui a funzionare anche in caso di temporanea indisponibilità del database.

---

## 9. Modulo Routing

Questo modulo implementa la logica di decisione del routing — il "cervello" del gateway. Determina dove instradare ogni richiesta SOAP in base a due dimensioni: il path HTTP (per identificare il backend di destinazione) e la configurazione dell'ente nel database (per determinare la modalità di instradamento).

### `RoutingDecision.java` (risultato immutabile)

**Tipo**: Classe immutabile  
**Scopo**: Contiene tutte le informazioni necessarie all'endpoint SOAP per instradare una richiesta.

**Campi** (3):

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `destinazione` | `BackendDestinatario` | Backend di destinazione (`MYPAY` o `MYPIVOT`), determinato dal path HTTP |
| `modalita` | `ModalitaRouting` | Modalità di instradamento (`PIATTAFORMA_UNITARIA` o `LEGACY`), determinata dal DB |
| `urlBackend` | `String` | URL base del backend, determinato da `BackendRoutingConfig` |

**Metodi di convenienza**:

| Metodo | Descrizione |
|--------|-------------|
| `isPiattaformaUnitaria()` | `true` se `modalita == PIATTAFORMA_UNITARIA` |
| `isLegacy()` | `true` se `modalita == LEGACY` |
| `getDestinazione()` | Restituisce il backend di destinazione |
| `getModalita()` | Restituisce la modalità di instradamento |
| `getUrlBackend()` | Restituisce l'URL base del backend |

---

### `RoutingDecisionService.java` (servizio di decisione)

**Tipo**: `@Service`  
**Scopo**: Servizio centrale di decisione del routing. Data una richiesta SOAP (identificata da `codIpaEnte`, `tipoOperazione` e `pathRichiesta`), produce una `RoutingDecision`.

**Dipendenze** (3, iniettate via costruttore):

| Dipendenza | Tipo | Scopo |
|-----------|------|-------|
| `pathRegistryConfig` | `PathRegistryConfig` | Risolve il path HTTP → backend di destinazione |
| `enteConfigCacheService` | `EnteConfigCacheService` | Recupera la configurazione dell'ente dalla cache/DB |
| `backendRoutingConfig` | `BackendRoutingConfig` | Fornisce l'URL base del backend di destinazione |

**Algoritmo di decisione a 3 passi**:

```
decide(codIpaEnte, tipoOperazione, pathRichiesta)
        │
        ▼
  Passo 1: Routing per path → destinazione backend
    pathRegistryConfig.resolveBackend(pathRichiesta)
        │
    Non trovato ──► PathNonRiconosciutoException
                    → SOAP Fault PATH_NON_RICONOSCIUTO (Client Fault)
        │
    Trovato ──► BackendDestinatario (MYPAY o MYPIVOT)
        │
        ▼
  Passo 2: Routing per modalità → PU o legacy
    enteConfigCacheService.findByCodIpaEnteAndTipoOperazione(codIpaEnte, tipoOperazione)
        │
    Non trovato ──► EnteNonCensitoException
                    → SOAP Fault ENTE_NON_AUTORIZZATO (Client Fault)
        │
    Trovato ──► EnteConfig (con modalitaRouting)
        │
        ▼
  Passo 3: Comporre la decisione
    urlBackend = backendRoutingConfig.getBaseUrlFor(destinazione)
        │
        ▼
    return RoutingDecision(destinazione, enteConfig.getModalitaRouting(), urlBackend)
```

**Importante**: Il servizio **non esegue alcuna comunicazione HTTP** — si limita a prendere la decisione. L'effettivo inoltro è responsabilità dell'endpoint SOAP (`ReconciliationEndpoint`) che utilizza `PiattaformaUnitariaClient` o `ProxyForwardingClient` in base alla decisione.

---

## 10. Modulo Endpoint SOAP

### `ReconciliationEndpoint.java`

**Tipo**: `@Endpoint` (Spring WS)  
**Scopo**: Riceve le richieste SOAP dai SIL e le instrada verso la Piattaforma Unitaria (con OAuth2) o verso il backend legacy (forward diretto), in base alla configurazione dell'ente nel database.

**Dipendenze iniettate** (5, tutte via costruttore):

| Dipendenza | Tipo | Scopo |
|-----------|------|-------|
| `piattaformaClient` | `PiattaformaUnitariaClient` | Inoltro verso la PU con OAuth2 |
| `proxyForwardingClient` | `ProxyForwardingClient` | Forward trasparente verso i backend legacy |
| `routingDecisionService` | `RoutingDecisionService` | Decisione di routing (path + DB → destinazione) |
| `transactionLoggingService` | `TransactionLoggingService` | Log transazionale su DB |
| `metricsService` | `MiddlewareMetricsService` | Raccolta metriche Micrometer |

**Dettagli tecnici**:
- Namespace: `http://www.regione.veneto.it/pagamenti/pivot/ente/`
- Header namespace: `http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead`
- Local part: `pivotSILAutorizzaImportFlussoTesoreria`
- Path di ricezione: qualsiasi path sotto `/ws/*` (il routing Spring WS avviene per namespace + localPart, non per path HTTP)

**Richiesta attesa dai SIL**:
```xml
<soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:ppt="http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead"
    xmlns:ente="http://www.regione.veneto.it/pagamenti/pivot/ente/">

    <soapenv:Header>
        <ppt:intestazionePPT>
            <codIpaEnte>SELC_99999000013</codIpaEnte>
        </ppt:intestazionePPT>
    </soapenv:Header>

    <soapenv:Body>
        <ente:pivotSILAutorizzaImportFlussoTesoreria>
            <password>BERGAMO</password>
            <tipoFlusso>O</tipoFlusso>
        </ente:pivotSILAutorizzaImportFlussoTesoreria>
    </soapenv:Body>

</soapenv:Envelope>
```

**Flusso interno di `handleReconciliationRequest()`**:

```
Richiesta SOAP dal SIL
        │
        ▼
 1. Estrae l'intero SOAP Envelope dal MessageContext
    (SoapMessage.writeTo → ByteArrayOutputStream → String)
        │
        ▼
 2. Estrae codIpaEnte dall'Header SOAP
    (extractCodIpaEnte: parsa l'Envelope XML,
     cerca <codIpaEnte> prima per tag name,
     poi per namespace HEADER_NAMESPACE_URI)
        │
        ▼
 3. Determina il path HTTP della richiesta originale
    (extractRequestPath: TransportContextHolder
     → HttpServletConnection → getRequestURI())
        │
        ▼
 4. Chiama routingDecisionService.decide(codIpaEnte, tipoOperazione, requestPath)
    → restituisce RoutingDecision { destinazione, modalita, urlBackend }
        │
   ┌────┴────────────────────────────────┐
   │                                      │
 PU (isPiattaformaUnitaria)           LEGACY (isLegacy)
   │                                      │
   ▼                                      ▼
 piattaformaClient                   proxyForwardingClient
  .forwardSoapRequest(                .forwardToLegacyBackend(
   PLATFORM_RECONCILIATION_PATH,       decision.getDestinazione(),
   fullSoapEnvelope)                   requestPath, fullSoapEnvelope)
   │                                      │
   └──────────────┬───────────────────────┘
                  │
                  ▼
 5. Estrae il contenuto del Body dalla risposta SOAP
    (extractBodyContent: cerca <Body> per namespace
     → restituisce primo Element figlio)
                  │
                  ▼
 6. Registra successo:
    - transactionLoggingService.logSuccesso(...)
    - metricsService.registraSuccesso(...)
                  │
                  ▼
 7. Restituisce l'Element a Spring WS
    (Spring WS lo ri-avvolge in un nuovo SOAP Envelope)
```

**Gestione errori**:
- **Errori pre-routing** (ente non censito, path non riconosciuto): usa `logErrorePreRouting()` con `modalitaRouting="SCONOSCIUTA"` e `destinazione="SCONOSCIUTA"`
- **Errori post-routing** (comunicazione fallita, timeout): usa `logErrore()` con la `RoutingDecision` già calcolata
- In entrambi i casi: registra l'errore nelle metriche con `metricsService.registraErrore()`
- Le eccezioni `RuntimeException` vengono propagate al `SoapFaultExceptionResolver`; le altre vengono avvolte in `RuntimeException`

**Metodo `extractCodIpaEnte(String soapEnvelope)`**:
- Parsifica l'intero Envelope XML usando `secureDocumentBuilderFactory`
- Cerca `<codIpaEnte>` prima con `getElementsByTagName("codIpaEnte")` (namespace-agnostic)
- Se non trovato, cerca con `getElementsByTagNameNS(HEADER_NAMESPACE_URI, "codIpaEnte")` (namespace-specific)
- Lancia `IllegalStateException` se non trovato in nessuna delle due modalità

**Metodo `extractRequestPath()`**:
- Usa `TransportContextHolder.getTransportContext()` per accedere alla connessione HTTP
- Castea a `HttpServletConnection` e chiama `getHttpServletRequest().getRequestURI()`
- Fallback a `/ws/pivot` se il `TransportContext` non è disponibile

> **NOTA**: Questo approccio "proxy trasparente" è necessario perché la PU richiede
> l'Header SOAP con `codIpaEnte` per identificare l'ente. Se si inoltrasse solo il
> Body (come farebbe un `@PayloadRoot` standard), la PU non saprebbe quale ente sta
> effettuando la richiesta.

**Sicurezza XML (prevenzione attacchi XXE)**:
Il parser XML è configurato con tutte le protezioni contro gli attacchi XXE (XML External Entity):
- `disallow-doctype-decl: true` — blocca le dichiarazioni DTD
- `external-general-entities: false` — disabilita le entità esterne
- `external-parameter-entities: false` — disabilita le entità di parametro esterne
- `XIncludeAware: false` — disabilita XInclude
- `expandEntityReferences: false` — non espande i riferimenti a entità
- `TransformerFactory`: attributi `ACCESS_EXTERNAL_DTD` e `ACCESS_EXTERNAL_STYLESHEET` impostati a stringa vuota

**Approccio contract-last**: L'endpoint è implementato senza un WSDL predefinito. Il contratto è definito dal codice Java (namespace + localPart). In fasi future si potrà migrare a contract-first con generazione da WSDL/XSD.

---

## 11. Gestione degli Errori

### `SoapFaultExceptionResolver.java`

**Tipo**: `@Component` + `EndpointExceptionResolver`  
**Scopo**: Intercetta tutte le eccezioni non gestite negli endpoint SOAP e le converte in **SOAP Fault** strutturate, garantendo che i SIL ricevano sempre una risposta SOAP valida anche in caso di errore.

**Mapping delle eccezioni**:

| Eccezione | SOAP Fault | Codice errore |
|-----------|-----------|---------------|
| `EnteNonCensitoException` | `Client/Sender Fault` | `ENTE_NON_AUTORIZZATO` |
| `PathNonRiconosciutoException` | `Client/Sender Fault` | `PATH_NON_RICONOSCIUTO` |
| `PiattaformaAuthenticationException` | `Server/Receiver Fault` | `AUTH_ERROR` |
| `PiattaformaCommunicationException` | `Server/Receiver Fault` | `COMM_ERROR` |
| Qualsiasi altra `Exception` | `Server/Receiver Fault` | `INTERNAL_ERROR` |

**Logica di classificazione**: Le eccezioni di routing (`EnteNonCensitoException`, `PathNonRiconosciutoException`) generano Fault **Client** perché rappresentano errori del chiamante (ente non autorizzato o path errato). Le eccezioni di comunicazione generano Fault **Server** perché rappresentano errori interni al middleware o ai backend.

**Struttura del SOAP Fault restituito al SIL**:
```xml
<soapenv:Fault>
    <faultcode>env:Server</faultcode>
    <faultstring xml:lang="it">Errore di comunicazione con la Piattaforma Unitaria: ...</faultstring>
    <detail>
        <fault:errorCode xmlns:fault="http://www.regione.veneto.it/pagamenti/pivot/ente/fault">
            COMM_ERROR
        </fault:errorCode>
    </detail>
</soapenv:Fault>
```

---

### `PiattaformaAuthenticationException.java`

**Tipo**: `RuntimeException`  
**Quando viene lanciata**:
- Credenziali OAuth2 non valide
- Endpoint di autenticazione non raggiungibile
- Risposta senza `access_token`
- Autenticazione fallita anche dopo il refresh del token

---

### `PiattaformaCommunicationException.java`

**Tipo**: `RuntimeException` con campo aggiuntivo `httpStatus`  
**Quando viene lanciata**:
- Timeout nella comunicazione HTTP
- Errore di rete (connessione rifiutata, DNS non risolvibile)
- Risposta HTTP 4xx o 5xx (diversa da 401)
- Circuit breaker aperto

Il campo `httpStatus` permette al `SoapFaultExceptionResolver` di includere il codice HTTP nel messaggio di errore restituito al SIL.

---

### `EnteNonCensitoException.java` (Fase 7)

**Tipo**: `RuntimeException` con campi aggiuntivi `codIpaEnte` e `tipoOperazione`  
**Quando viene lanciata**:
- L'ente non è presente nella tabella `mwpay_ente_config`
- L'ente è presente ma non ha una regola di routing attiva per il tipo di operazione richiesto

**SOAP Fault generato**: `Client/Sender Fault` con codice `ENTE_NON_AUTORIZZATO`

---

### `PathNonRiconosciutoException.java` (Fase 7)

**Tipo**: `RuntimeException` con campo aggiuntivo `requestPath`  
**Quando viene lanciata**:
- Il path HTTP della richiesta non corrisponde a nessun backend configurato nel `PathRegistryConfig`

**SOAP Fault generato**: `Client/Sender Fault` con codice `PATH_NON_RICONOSCIUTO`

---

## 12. Monitoraggio e Health Check

Il middleware espone endpoint di monitoraggio tramite **Spring Boot Actuator**.

### URL degli endpoint Actuator

| Endpoint | URL | Descrizione |
|----------|-----|-------------|
| Health | `GET /actuator/health` | Stato generale del sistema |
| Info | `GET /actuator/info` | Informazioni applicazione |
| Metrics | `GET /actuator/metrics` | Metriche JVM e applicative |
| Circuit Breakers | `GET /actuator/circuitbreakers` | Stato dei circuit breaker |
| Retries | `GET /actuator/retries` | Statistiche dei retry |

### `OAuthTokenHealthIndicator.java`

**Tipo**: `@Component` + `HealthIndicator`  
**Scopo**: Verifica se il token OAuth2 in cache è ancora valido.

**Logica**:
- `UP`: token presente e non scaduto
- `DOWN`: token assente o scaduto (il middleware lo rinnoverà automaticamente al prossimo utilizzo — questo stato è normale al primo avvio)

---

### `PiattaformaUnitariaHealthIndicator.java`

**Tipo**: `@Component` + `HealthIndicator`  
**Scopo**: Verifica la raggiungibilità della Piattaforma Unitaria.

**Logica**:
- Effettua una GET leggera verso la base URL della Piattaforma Unitaria
- Timeout ridotti: connect 3s, read 5s
- `UP`: la piattaforma risponde
- `DOWN`: timeout, errore di rete, o risposta di errore

---

### `EnteConfigHealthIndicator.java` (Fase 9)

**Tipo**: `@Component` + `HealthIndicator`  
**Scopo**: Verifica che nel sistema siano configurati enti attivi per il routing.

**Logica**:
- Interroga `EnteConfigCacheService.size()` per ottenere il numero di enti configurati
- `UP`: se `entiConfigurati > 0` — almeno un ente configurato e attivo
- `DOWN`: se la cache è vuota (nessun ente configurato)
- `DOWN`: se si verifica un'eccezione durante il controllo (errore DB)
- Il numero di enti configurati viene esposto come dettaglio nell'health check

**Esempio di risposta Actuator**:
```json
{
  "status": "UP",
  "details": {
    "entiConfigurati": 3
  }
}
```

---

### `MiddlewareMetricsService.java` (Fase 9)

**Tipo**: `@Service`  
**Scopo**: Espone metriche operative del middleware tramite Micrometer, consultabili da Spring Boot Actuator (`/actuator/metrics`).

**Metriche registrate**:

| Metrica | Tipo Micrometer | Tag | Descrizione |
|---------|----------------|-----|-------------|
| `middleware.richieste.totali` | Counter | `ente`, `operazione`, `modalita`, `destinazione`, `esito` | Conteggio richieste per combinazione |
| `middleware.richieste.durata` | Timer | `operazione`, `modalita`, `destinazione` | Distribuzione durata richieste (ms) |
| `middleware.enti.configurati` | Gauge | — | Numero di enti attivi nella configurazione (collegato a `EnteConfigCacheService.size()`) |

**Metodi principali**:

| Metodo | Descrizione |
|--------|-------------|
| `registraSuccesso(codIpaEnte, tipoOperazione, decision, durataMs)` | Incrementa contatore con `esito=OK` e registra durata nel timer |
| `registraErrore(codIpaEnte, tipoOperazione, decision, durataMs)` | Incrementa contatore con `esito=ERRORE` e registra durata nel timer |

**Robustezza**: I parametri `null` vengono sostituiti con `"sconosciuto"`. Le eccezioni durante la registrazione delle metriche vengono catturate silenziosamente per non bloccare il flusso principale.

---

## 13. Resilienza

Il middleware implementa la resilienza tramite la libreria **Resilience4j** (versione Spring Boot 3), applicata al metodo `PiattaformaUnitariaClient.forwardSoapRequest()`.

### Cos'è Resilience4j e come si usa

**Resilience4j** è una libreria Java leggera e modulare per la resilienza delle applicazioni distribuite. È progettata per funzionare con programmazione funzionale (lambda) e si integra nativamente con Spring Boot 3 tramite il modulo `resilience4j-spring-boot3`.

La libreria fornisce i seguenti pattern di resilienza:

| Pattern | Descrizione |
|---------|-------------|
| **Circuit Breaker** | Apre il circuito quando ci sono troppi errori consecutivi, impedendo ulteriori chiamate a un servizio non disponibile. Dopo un periodo di attesa, permette alcune chiamate di test ("half-open") per verificare se il servizio è tornato disponibile. |
| **Retry** | Riprova automaticamente le operazioni fallite con diversi strategie di backoff (lineare, esponenziale, fisso). |
| **Rate Limiter** | Limita il numero di chiamate in un intervallo di tempo configurabile. |
| **Bulkhead** | Isola le risorse per evitare che un errore in un componente si propaghi a tutto il sistema. |

#### Dipendenza Maven

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

La dipendenza `spring-boot-starter-aop` è necessaria perché Resilience4j utilizza AspectJ per intercettare le chiamate ai metodi annotati.

#### Utilizzo nel codice

I pattern di resilienza si applicano tramite **annotazioni** sui metodi. Nel progetto, il `PiattaformaUnitariaClient.forwardSoapRequest()` è decorato con:

```java
@CircuitBreaker(name = "piattaformaUnitaria", fallbackMethod = "forwardSoapRequestFallback")
@Retry(name = "piattaformaUnitaria")
public String forwardSoapRequest(String path, String soapXml) { ... }
```

- **`@CircuitBreaker(name = "piattaformaUnitaria")`**: Applica il circuit breaker denominato "piattaformaUnitaria". Se il circuit breaker è aperto, non chiama il metodo ma invoca direttamente il metodo di fallback.
- **`fallbackMethod = "forwardSoapRequestFallback"`**: Specifica il metodo da chiamare quando il circuit breaker è aperto o la chiamata fallisce. Il fallback deve avere la stessa firma del metodo originale più un parametro `Throwable` per l'eccezione.
- **`@Retry(name = "piattaformaUnitaria")`**: Applica la strategia di retry denominata "piattaformaUnitaria".

#### Configurazione

La configurazione avviene nel file `application.properties` sotto le chiavi `resilience4j.*`:

```properties
# Circuit Breaker
resilience4j.circuitbreaker.instances.piattaformaUnitaria.sliding-window-type=COUNT_BASED
resilience4j.circuitbreaker.instances.piattaformaUnitaria.sliding-window-size=10
resilience4j.circuitbreaker.instances.piattaformaUnitaria.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.piattaformaUnitaria.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.piattaformaUnitaria.permitted-number-of-calls-in-half-open-state=3
# Retry
resilience4j.retry.instances.piattaformaUnitaria.max-attempts=3
resilience4j.retry.instances.piattaformaUnitaria.wait-duration=1s
resilience4j.retry.instances.piattaformaUnitaria.enable-exponential-backoff=true
resilience4j.retry.instances.piattaformaUnitaria.exponential-backoff-multiplier=2
```

#### Monitoraggio

Resilience4j espone le metriche tramite Spring Boot Actuator. Gli endpoint disponibili sono:

- `/actuator/circuitbreakers`: Stato dei circuit breaker
- `/actuator/retries`: Statistiche dei retry
- `/actuator/circuitbreakers/events`: Eventi di transizione degli stati

### Circuit Breaker (`piattaformaUnitaria`)

Il circuit breaker protegge il sistema da chiamate continue a un servizio non disponibile.

```
       CLOSED                    OPEN
    (funziona)  ──soglia errori──►  (bloccato)
         ▲                              │
         │                         attesa (30s)
         │                              │
         └─── test OK ─── HALF_OPEN ◄──┘
                          (3 prove)
```

**Parametri di default** (profilo base):
- Finestra: ultime **10 chiamate** (`COUNT_BASED`)
- Soglia di apertura: **50%** di fallimenti
- Attesa in stato OPEN: **30 secondi**
- Chiamate in HALF_OPEN: **3**
- Eccezioni registrate: `PiattaformaCommunicationException`, `ResourceAccessException`
- Eccezioni ignorate: `PiattaformaAuthenticationException` (gestita con refresh)

**Fallback**: Quando il circuit breaker è aperto, viene invocato `forwardSoapRequestFallback()` che lancia una `PiattaformaCommunicationException` con HTTP 503, che il `SoapFaultExceptionResolver` converte in `COMM_ERROR`.

### Retry (`piattaformaUnitaria`)

Il retry riprova automaticamente le chiamate fallite con backoff esponenziale.

**Parametri di default**:
- Tentativi massimi: **3**
- Attesa iniziale: **1 secondo**
- Moltiplicatore esponenziale: **2x** (1s → 2s → 4s)
- Riprova su: `PiattaformaCommunicationException`, `ResourceAccessException`
- Non riprova su: `PiattaformaAuthenticationException`

---

## 14. Ambienti e Profili

Il progetto utilizza attualmente **un solo profilo attivo**: `dev`. I profili `uat` e `prod` sono stati rimossi per semplificare l'ambiente di sviluppo; verranno ricreati quando necessario per il deployment in ambienti superiori.

Tutti i file di configurazione sono in formato **`.properties`** (la migrazione da `.yml` è avvenuta contestualmente alla semplificazione dei profili).

### Panoramica profili

| Profilo | Stato | Piattaforma target | Sicurezza | Logging |
|---------|-------|-------------------|-----------|---------|
| `dev` | **Attivo** | `api.uat.p4pa.pagopa.it` | JWT disabilitato (anonymous) | DEBUG |
| `uat` | Da creare | `api.uat.p4pa.pagopa.it` | JWT abilitato | INFO |
| `prod` | Da creare | URL da env var | JWT abilitato + segreti da env var | WARN |
| *(base)* | Sempre attivo | `api.uat.p4pa.pagopa.it` | JWT su `/ws/**` | INFO |

---

### Profilo `dev` (`application-dev.properties`)

**Quando usarlo**: Sviluppo che richiede connessione reale all'ambiente UAT di pagoPA.

**Caratteristiche**:
- Punta all'ambiente UAT reale (`api.uat.p4pa.pagopa.it`)
- Credenziali OAuth2 da variabili d'ambiente (`PIATTAFORMA_CLIENT_ID`, `PIATTAFORMA_CLIENT_SECRET`) o dal file `.env` (gitignored)
- **Sicurezza JWT disabilitata** — i SIL non inviano JWT; l'autenticazione avviene tramite `codIpaEnte` + `password` nel body SOAP
- Configurazione SpringLine2 security: `jwt.enabled=false`, `anonymous` per `/**`
- Logging DEBUG per il codice del middleware, Spring WS e RestTemplate
- Actuator health con dettagli sempre visibili (`show-details=always`)
- Resilience4j con parametri rilassati per non ostacolare il debugging (soglia 80%, attesa 10s)
- Database PostgreSQL locale (`localhost:5432/mypay_local_copy`), con supporto override tramite variabili d'ambiente `DB_PA_URL`, `DB_PA_USERNAME`, `DB_PA_PASSWORD`

---

### Profilo base (senza suffisso — `application.properties`)

È la configurazione condivisa, sempre caricata. Il profilo `dev` sovrascrive solo le proprietà che gli servono.

**Configurazioni principali**:
- Nessuna configurazione JPA/Hibernate: il layer dati usa JDBC + Jdbi
- Piattaforma target: `api.uat.p4pa.pagopa.it` (default UAT)
- Resilience4j: parametri standard (finestra 10 chiamate, soglia 50%, attesa OPEN 30s)
- Actuator: endpoints `health, info, metrics, circuitbreakers, retries`
- SpringLine2 security: JWT richiesto su `/ws/**`, anonymous su Swagger e Actuator health
- La configurazione del DataSource **non è nel profilo base** — ogni profilo dichiara la propria connessione

---

### Profili `uat` e `prod` (da creare in futuro)

I profili `uat` e `prod` sono stati rimossi nella fase di semplificazione dell'ambiente di sviluppo. Verranno ricreati come file `application-uat.properties` e `application-prod.properties` quando necessario per il deployment.

**Variabili d'ambiente previste per il profilo `prod`** (da usare quando verrà creato):

| Variabile | Descrizione |
|-----------|-------------|
| `PIATTAFORMA_BASE_URL` | URL base della Piattaforma Unitaria |
| `PIATTAFORMA_CLIENT_ID` | Client ID OAuth2 |
| `PIATTAFORMA_CLIENT_SECRET` | Client secret OAuth2 |
| `SPL_JWT_CYPHER_SECRET` | Segreto per cifratura JWT SpringLine2 |

---

## 15. Test Unitari

Il progetto ha **124 test unitari** suddivisi in 14 classi, tutti con risultato BUILD SUCCESS.

### `OAuthTokenServiceTest` — 9 test

Testa il servizio di autenticazione OAuth2 in isolamento completo (nessuna chiamata HTTP reale).

| Test | Cosa verifica |
|------|---------------|
| `shouldRequestNewTokenWhenCacheIsEmpty` | Prima chiamata: richiede il token all'endpoint OAuth2 |
| `shouldReturnCachedTokenWhenValid` | Seconda chiamata: usa la cache senza fare HTTP |
| `shouldRefreshTokenWhenExpired` | Token scaduto: richiede automaticamente uno nuovo |
| `shouldRefreshTokenOnDemand` | `refreshToken()` forza il rinnovo anche se il token è valido |
| `shouldInvalidateToken` | `invalidateToken()` svuota la cache correttamente |
| `shouldThrowExceptionWhenTokenEndpointFails` | Gestione errore HTTP sull'endpoint OAuth2 |
| `shouldThrowExceptionWhenResponseBodyIsNull` | Gestione risposta nulla dall'endpoint OAuth2 |
| `shouldThrowExceptionWhenAccessTokenIsNull` | Gestione risposta senza `access_token` |
| `isTokenValid_returnsFalseWhenNoToken` | `isTokenValid()` ritorna false senza token in cache |

> **Nota**: I mock degli stub per l'URL del token usano `argThat(url -> url.startsWith(TOKEN_URL))`
> invece di `eq(TOKEN_URL)` perché ora l'URL include i parametri OAuth2 come query string.

### `PiattaformaUnitariaClientTest` — 7 test

Testa il client HTTP verso la Piattaforma Unitaria.

| Test | Cosa verifica |
|------|---------------|
| `shouldForwardSoapRequestSuccessfully` | Inoltro normale con risposta 200 |
| `shouldRetryWithNewTokenOn401` | Ricezione 401: refresh del token e retry automatico |
| `shouldThrowAuthExceptionWhenRetryAlsoFails401` | Secondo tentativo fallisce: lancia `PiattaformaAuthenticationException` |
| `shouldThrowCommExceptionOnHttpError` | Risposta HTTP 500: lancia `PiattaformaCommunicationException` |
| `shouldThrowCommExceptionOnTimeout` | Timeout di rete: lancia `PiattaformaCommunicationException` |
| `shouldUseFallbackWhenCircuitBreakerOpen` | Circuit breaker aperto: usa il fallback |
| `shouldBuildCorrectUrl` | URL costruito correttamente come baseUrl + path |

### `ReconciliationEndpointTest` — 18 test (Fase 2→7→9)

Testa l'endpoint SOAP di riconciliazione con routing dinamico, logging e metriche. Organizzata in 3 nested inner class.

#### `FlussoBase` — 7 test

| Test | Cosa verifica |
|------|---------------|
| `flussoPU_completo_estraeEnvelopeInoltraERispondeAlSIL` | Flusso completo PU: estrae Envelope, inoltra alla PU, estrae body dalla risposta |
| `flussoLegacy_completo_forwardDirettoAlBackend` | Flusso completo legacy: forward diretto al backend senza OAuth2 |
| `erroreClient_vienePropagatoComeRuntimeException` | Eccezione dal client: rilancia come RuntimeException |
| `extractCodIpaEnte_estraeDaHeaderSoap` | Estrae correttamente codIpaEnte dall'Header SOAP |
| `extractRequestPath_usaTransportContext` | Usa TransportContextHolder per estrarre il path HTTP |
| `extractBodyContent_estraeContenutoBodyDaEnvelope` | Il body estratto dalla risposta è corretto (solo contenuto del Body) |
| `costanti_hannoValoriCorretti` | Le costanti di namespace, path e tipo operazione hanno i valori corretti |

#### `Routing` — 5 test

| Test | Cosa verifica |
|------|---------------|
| `routingPU_usaPiattaformaClient` | In modalità PU, usa `PiattaformaUnitariaClient` |
| `routingLegacy_usaProxyForwardingClient` | In modalità legacy, usa `ProxyForwardingClient` |
| `enteNonCensito_propagaEccezione` | `EnteNonCensitoException` viene propagata al SoapFaultExceptionResolver |
| `pathNonRiconosciuto_propagaEccezione` | `PathNonRiconosciutoException` viene propagata al SoapFaultExceptionResolver |
| `requestPath_vienPassatoAlRoutingDecisionService` | Il path HTTP della richiesta viene inviato al `RoutingDecisionService` |

#### `LoggingEMetriche` — 6 test

| Test | Cosa verifica |
|------|---------------|
| `successo_registraLogTransazionale` | Su successo, chiama `transactionLoggingService.logSuccesso()` |
| `successo_registraMetrica` | Su successo, chiama `metricsService.registraSuccesso()` |
| `errorePostRouting_registraLogConDecision` | Su errore post-routing, chiama `logErrore()` con la RoutingDecision |
| `errorePreRouting_registraLogSenzaDecision` | Su errore pre-routing, chiama `logErrorePreRouting()` |
| `errore_registraMetricaErrore` | Su errore, chiama `metricsService.registraErrore()` |
| `eccezioneGenerica_vieneAvvoltaInRuntimeException` | Eccezione non-runtime viene avvolta in RuntimeException |

> **Nota**: I test mockano `MessageContext`, `SoapMessage`, `TransportContext` e tutti i servizi
> per simulare i flussi completi in isolamento.

### `PathRegistryConfigTest` — 12 test (Fase 5)

Testa il registro configurabile di path-prefix → backend.

| Test | Cosa verifica |
|------|---------------|
| `init_conMappaValida_convertePathCorrettamente` | Conversione `ws-pivot` → `/ws/pivot` |
| `init_conMappaVuota_lanciaEccezione` | Validazione `@PostConstruct` con mappa vuota |
| `init_conMappaNulla_lanciaEccezione` | Validazione `@PostConstruct` con mappa nulla |
| `resolveBackend_pathEsatto_trovaBackend` | Path esatto `/ws/pivot` → `MYPIVOT` |
| `resolveBackend_pathConSottopercorso_trovaBackend` | Path con sotto-percorso `/ws/pivot/foo` → `MYPIVOT` |
| `resolveBackend_pathNonRegistrato_restituisceVuoto` | Path non registrato → `Optional.empty()` |
| `resolveBackend_pathNull_restituisceVuoto` | Path null → `Optional.empty()` |
| `resolveBackend_pathVuoto_restituisceVuoto` | Path vuoto → `Optional.empty()` |
| `resolveBackend_pathMypay_trovaBackend` | `/ws/pa` → `MYPAY` |
| `resolveBackend_pathFesp_trovaBackend` | `/ws/fesp` → `MYPAY` |
| `resolveBackend_longestPrefixMatching` | Longest-prefix matching corretto |
| `resolveBackend_pathSenzaSlashIniziale` | Path senza slash iniziale gestito correttamente |

### `BackendRoutingConfigTest` — 3 test (Fase 5)

Testa la configurazione degli URL dei backend.

| Test | Cosa verifica |
|------|---------------|
| `getBaseUrlFor_mypay_restituisceUrlCorretto` | `MYPAY` → URL mypay |
| `getBaseUrlFor_mypivot_restituisceUrlCorretto` | `MYPIVOT` → URL mypivot |
| `getBaseUrlFor_backendNonRiconosciuto_lanciaEccezione` | Backend sconosciuto → eccezione |

### `ProxyForwardingClientTest` — 6 test (Fase 5)

Testa il client di forward trasparente verso i backend legacy.

| Test | Cosa verifica |
|------|---------------|
| `forwardToBackend_success` | Forward riuscito con risposta 200 |
| `forwardToBackend_httpError` | Errore HTTP 500 → `PiattaformaCommunicationException` |
| `forwardToBackend_timeout` | Timeout di rete → `PiattaformaCommunicationException` |
| `forwardToBackend_correctUrl` | URL costruito come `backendBaseUrl + path` |
| `forwardToBackend_contentType` | Header Content-Type impostato a `text/xml` |
| `forwardToBackendFallback_circuitBreakerOpen` | Fallback quando circuit breaker è aperto |

### `DomainModelTest` — 10 test (Fase 6)

Testa i modelli di dominio `EnteConfig`, `TransactionLog` e l'enum `ModalitaRouting`.

| Test | Cosa verifica |
|------|---------------|
| `enteConfig_creazione_conTuttiICampi` | Creazione corretta di EnteConfig con tutti gli 8 campi |
| `enteConfig_creazione_conBuilderMinimo` | Creazione con solo i campi obbligatori |
| `enteConfig_modalitaRouting_piattaformaUnitaria` | Modalità PIATTAFORMA_UNITARIA assegnata correttamente |
| `enteConfig_modalitaRouting_legacy` | Modalità LEGACY assegnata correttamente |
| `transactionLog_creazione_conTuttiICampi` | Creazione corretta di TransactionLog con tutti gli 11 campi |
| `transactionLog_creazione_conCampiMinimi` | Creazione con solo i campi obbligatori |
| `transactionLog_esitoOk` | Esito "OK" impostato correttamente |
| `transactionLog_esitoErrore` | Esito "ERRORE" con messaggio di errore |
| `modalitaRouting_valori` | L'enum ha esattamente 2 valori: PIATTAFORMA_UNITARIA e LEGACY |
| `modalitaRouting_valueOf` | `valueOf()` funziona correttamente per entrambi i valori |

### `EnteConfigRowMapperTest` — 3 test (Fase 6)

Testa il mapping dei risultati SQL verso il modello `EnteConfig`.

| Test | Cosa verifica |
|------|---------------|
| `map_conTuttiICampi_creaEnteConfigCorretto` | Mapping completo da ResultSet a EnteConfig |
| `map_conCampiNull_gestisceCorrettamente` | Gestione corretta dei campi nullable |
| `map_conModalitaRoutingLegacy_mappataCorrettamente` | Mapping corretto per modalità LEGACY |

### `EnteConfigCacheServiceTest` — 11 test (Fase 6)

Testa la cache in-memory con TTL per la configurazione degli enti.

| Test | Cosa verifica |
|------|---------------|
| `getConfig_primaChiamata_caricaDaDatabase` | Prima chiamata: carica da DB |
| `getConfig_secondaChiamata_usaCache` | Seconda chiamata: usa la cache senza query DB |
| `getConfig_dopoTTL_ricaricaDaDatabase` | Dopo scadenza TTL: ricarica da DB |
| `getConfig_enteNonTrovato_restituisceVuoto` | Ente non trovato → `Optional.empty()` |
| `getConfig_chiaveCorretta` | La chiave di cache usa il formato `codIpaEnte\|tipoOperazione` |
| `invalidate_svuotaCache` | `invalidate()` svuota tutta la cache |
| `size_restituisceDimensioneCorretta` | `size()` restituisce il numero di entry nella cache |
| `caricamentoIniziale_thread_safety` | Caricamento thread-safe con `ReentrantLock` |
| `staleWhileRevalidate_restituisceVecchioDatiDuranteRicaricamento` | Restituisce dati stale durante il refresh |
| `ttlConfigurabile_leggeValore` | TTL letto da `middleware.cache.ente-config.ttl-seconds` |
| `costruttore_inizializzaCorrettamente` | Inizializzazione corretta delle strutture interne |

### `RoutingDecisionServiceTest` — 13 test (Fase 7)

Testa il servizio di decisione del routing (il "cervello" del gateway). Organizzata in 4 nested inner class.

| Nested class | # Test | Cosa verifica |
|-------------|--------|---------------|
| `RoutingPiattaformaUnitaria` | 3 | Routing verso PU: decisione corretta, URL backend, modalità |
| `RoutingLegacy` | 3 | Routing verso legacy: decisione corretta, URL backend, modalità |
| `CasiDiErrore` | 4 | Ente non censito, path non riconosciuto, ente disattivato, configurazione assente |
| `InterazioneDipendenze` | 3 | Ordine di chiamata dei servizi, propagazione eccezioni, composizione decisione |

### `SoapFaultExceptionResolverTest` — 8 test (Fase 7)

Testa la mappatura delle eccezioni in SOAP Fault strutturate.

| Test | Cosa verifica |
|------|---------------|
| `piattaformaAuthenticationException_generaServerFault_AUTH_ERROR` | Auth exception → Server Fault `AUTH_ERROR` |
| `piattaformaCommunicationException_generaServerFault_COMM_ERROR` | Comm exception → Server Fault `COMM_ERROR` |
| `genericaException_generaServerFault_INTERNAL_ERROR` | Eccezione generica → Server Fault `INTERNAL_ERROR` |
| `enteNonCensitoException_generaClientFault_ENTE_NON_AUTORIZZATO` | Ente non censito → Client Fault `ENTE_NON_AUTORIZZATO` |
| `pathNonRiconosciutoException_generaClientFault_PATH_NON_RICONOSCIUTO` | Path non riconosciuto → Client Fault `PATH_NON_RICONOSCIUTO` |
| `faultString_contieneMessaggioEccezione` | Il messaggio dell'eccezione appare nel faultstring |
| `faultDetail_contieneErrorCode` | Il codice errore appare nel detail |
| `erroreNelResolver_nonPropagaEccezione` | Se il resolver stesso fallisce, non propaga l'eccezione |

### `TransactionLoggingServiceTest` — 11 test (Fase 9)

Testa il servizio di log transazionale su DB. Organizzata in 5 nested inner class.

| Nested class | # Test | Cosa verifica |
|-------------|--------|---------------|
| `LogSuccesso` | 2 | Inserimento corretto nel DB, campi mappati correttamente |
| `LogErrore` | 3 | Errore post-routing con decision, campi errore, HTTP status null |
| `LogErrorePreRouting` | 2 | Errore pre-routing con modalità/destinazione "SCONOSCIUTA" |
| `Troncamento` | 2 | Messaggio errore troncato a 1000 caratteri, messaggio corto non troncato |
| `ResilienzaDB` | 2 | Errore DB non blocca il flusso principale, warning nel log applicativo |

### `EnteConfigHealthIndicatorTest` — 4 test (Fase 9)

Testa l'health indicator per la configurazione degli enti.

| Test | Cosa verifica |
|------|---------------|
| `health_conEntiConfigurati_restituisceUP` | Cache con enti → UP con dettaglio `entiConfigurati` |
| `health_conUnEnteConfigurato_restituisceUP` | Cache con 1 ente → UP |
| `health_conCacheVuota_restituisceDOWN` | Cache vuota → DOWN |
| `health_conEccezione_restituisceDOWN` | Eccezione durante controllo → DOWN con dettaglio errore |

### `MiddlewareMetricsServiceTest` — 12 test (Fase 9)

Testa il servizio di metriche Micrometer. Organizzata in 5 nested inner class.

| Nested class | # Test | Cosa verifica |
|-------------|--------|---------------|
| `GaugeEntiConfigurati` | 1 | Gauge collegato a `EnteConfigCacheService.size()` |
| `ContatoreSuccesso` | 3 | Contatore incrementato con esito OK, tag corretti, parametri null gestiti |
| `ContatoreErrore` | 3 | Contatore incrementato con esito ERRORE, tag corretti, decision null gestita |
| `TimerDurata` | 3 | Timer registra durata, tag corretti, durata zero gestita |
| `NomiMetriche` | 2 | Nomi metriche corretti (`middleware.richieste.totali`, `middleware.richieste.durata`) |

### Riepilogo test

| Classe di test | # Test | Fase |
|---------------|--------|------|
| `OAuthTokenServiceTest` | 9 | Fase 2 |
| `PiattaformaUnitariaClientTest` | 7 | Fase 2 |
| `ReconciliationEndpointTest` | 18 | Fase 2→7→9 |
| `PathRegistryConfigTest` | 12 | Fase 5 |
| `BackendRoutingConfigTest` | 3 | Fase 5 |
| `ProxyForwardingClientTest` | 6 | Fase 5 |
| `DomainModelTest` | 10 | Fase 6 |
| `EnteConfigRowMapperTest` | 3 | Fase 6 |
| `EnteConfigCacheServiceTest` | 11 | Fase 6 |
| `RoutingDecisionServiceTest` | 13 | Fase 7 |
| `SoapFaultExceptionResolverTest` | 8 | Fase 7 |
| `TransactionLoggingServiceTest` | 11 | Fase 9 |
| `EnteConfigHealthIndicatorTest` | 4 | Fase 9 |
| `MiddlewareMetricsServiceTest` | 12 | Fase 9 |
| **Totale** | **124** | **BUILD SUCCESS** |

---

## 16. Come avviare il progetto

### Pre-requisiti

- Java 17 (`C:\Program Files\Java\jdk-17`)
- Maven 3.9.9 (`C:\Program Files\apache-maven\apache-maven-3.9.9`)
- Ambiente Windows con WSL (per gli strumenti di sviluppo)

### Compilazione

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn compile -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

### Esecuzione dei test

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn test -f mypay.mypaycore-springboot/pom.xml -Denforcer.skip=true"
```

### Build completa (tutti i moduli)

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn clean install -Denforcer.skip=true"
```

### Avvio in modalità dev (connessione a PU UAT reale)

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& set PIATTAFORMA_CLIENT_SECRET=<client-secret>&& mvn spring-boot:run -f mypay.mypaycore-springboot/pom.xml -Denforcer.skip=true -Dspring-boot.run.profiles=dev"
```

> **NOTA**: Sostituire `<client-secret>` con il valore reale. Le credenziali sono nel file `.env` (gitignored).
> Richiede PostgreSQL attivo su `localhost:5432/mypay_local_copy`.

### Configurazione IntelliJ IDEA

1. **Run → Edit Configurations → + → Maven**
2. Configurare:
   - **Name**: `MyPayCore — dev`
   - **Command**: `spring-boot:run -f mypay.mypaycore-springboot/pom.xml -Denforcer.skip=true -Dspring-boot.run.profiles=dev`
   - **Working directory**: root del progetto (`mypay.mypaycore`)

### Note sul flag `-Denforcer.skip=true`

Il POM padre corporativo (`it.ariaspa:cm:1.0.0`) ha un plugin enforcer che verifica che il sistema operativo sia Unix. Poiché lo sviluppo avviene su Windows, questo flag è necessario per bypassare il controllo. **Non usarlo mai in ambienti CI/CD che girano su Linux**.

---

## 17. Come testare il flusso completo

### 17.1 Test con profilo `dev` (PU UAT reale) — TEST END-TO-END

Con l'applicazione avviata in profilo `dev` su `http://localhost:8080`:

#### Pre-requisiti

1. PostgreSQL attivo su `localhost:5432/mypay_local_copy` (user: admin, password: admin)
2. Variabile d'ambiente `PIATTAFORMA_CLIENT_SECRET` impostata con il client-secret OAuth2 reale
3. Connettività di rete verso `api.uat.p4pa.pagopa.it`

#### Avvio

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& set PIATTAFORMA_CLIENT_SECRET=<client-secret>&& mvn spring-boot:run -f mypay.mypaycore-springboot/pom.xml -Denforcer.skip=true -Dspring-boot.run.profiles=dev"
```

#### Passo 1 — Health check

```
GET http://localhost:8080/actuator/health
```

Risposta attesa (componenti chiave):
- `db`: UP — PostgreSQL connesso
- `piattaformaUnitaria`: UP — PU UAT raggiungibile
- `OAuthToken`: DOWN al primo avvio (normale — il token viene acquisito alla prima richiesta SOAP)

#### Passo 2 — Chiamata SOAP reale verso PU

Importare in **Postman**: `requests/MyPay-Middleware-Dev.postman_collection.json`

Oppure chiamata diretta:

```
POST http://localhost:8080/ws/pivot/PagamentiTelematiciPagatiRiconciliati
Content-Type: text/xml;charset=UTF-8
```

Oppure chiamata diretta:

```xml
<soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:ppt="http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead"
    xmlns:ente="http://www.regione.veneto.it/pagamenti/pivot/ente/">
    <soapenv:Header>
        <ppt:intestazionePPT>
            <codIpaEnte>SELC_99999000013</codIpaEnte>
        </ppt:intestazionePPT>
    </soapenv:Header>
    <soapenv:Body>
        <ente:pivotSILAutorizzaImportFlussoTesoreria>
            <password>BERGAMO</password>
            <tipoFlusso>O</tipoFlusso>
        </ente:pivotSILAutorizzaImportFlussoTesoreria>
    </soapenv:Body>
</soapenv:Envelope>
```

**Risposta attesa dalla PU reale** (HTTP 200):
```xml
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
    <SOAP-ENV:Header/>
    <SOAP-ENV:Body>
        <ns3:pivotSILAutorizzaImportFlussoTesoreriaRisposta
            xmlns:ns3="http://www.regione.veneto.it/pagamenti/pivot/ente/">
            <uploadUrl>https://api.uat.p4pa.pagopa.it/pu/fileshare/organization/...</uploadUrl>
            <authorizationToken>AUTHORIZATIONTOKEN</authorizationToken>
            <requestToken>XXXX</requestToken>
            <importPath>/IMPORTPATH</importPath>
        </ns3:pivotSILAutorizzaImportFlussoTesoreriaRisposta>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

**Flusso interno reale**:
```
Postman → ReconciliationEndpoint (Spring WS)
            → estrae Envelope SOAP completo (Header + Body)
            → PiattaformaUnitariaClient.forwardSoapRequest()
                → OAuthTokenInterceptor.intercept()
                    → OAuthTokenService.getAccessToken()
                        → POST api.uat.p4pa.pagopa.it/pu/auth/oauth/token?client_id=...&...
                        ← Token OAuth2 reale (validità ~4 ore)
                → POST api.uat.p4pa.pagopa.it/pu/sil/soap/reconciliation/...
                   (con Authorization: Bearer <token-reale>)
                ← Risposta SOAP reale dalla PU
            → estrae body dalla risposta PU
            → risposta SOAP a Postman
```

#### Passo 3 — Verifica token in cache

Dopo la prima richiesta SOAP, verificare che il token sia stato acquisito:

```
GET http://localhost:8080/actuator/health/OAuthToken
```

Risposta attesa: `{"status":"UP","details":{"stato":"Token OAuth2 in cache valido"}}`

---

## 18. Cosa NON è ancora implementato

Questa sezione è fondamentale per chi prende in carico il progetto: elenca esplicitamente le funzionalità **intenzionalmente escluse** dalle fasi completate finora (Fasi 1-7 e 9).

| Funzionalità | Stato | Fase prevista | Note |
|-------------|-------|---------------|------|
| Schema e tabelle del database PostgreSQL | ✅ Implementato | Fase 6 | Tabelle `mwpay_ente_config` e `mwpay_transaction_log`, DAO Jdbi, cache TTL |
| Routing per modalità (PU vs legacy) | ✅ Implementato | Fase 7 | `RoutingDecisionService` — decide dove instradare in base a path + DB |
| Log transazionale, audit, metriche | ✅ Implementato | Fase 9 | `TransactionLoggingService`, `MiddlewareMetricsService`, `EnteConfigHealthIndicator` |
| Logica di business (riconciliazione, tesoreria) | Non implementata | — | L'endpoint attuale fa solo forwarding del payload |
| Trasformazione payload SOAP | Non implementata | — | Il payload viene inoltrato così com'è senza modifiche |
| Validazione business dei dati in ingresso | Non implementata | — | Spring WS valida solo il namespace/localPart |
| Endpoint SOAP aggiuntivi | Non implementati | Fase 8 (bloccata) | Solo `pivotSILAutorizzaImportFlussoTesoreria` — altri da censire con i team mypay/mypivot |
| Contract-first (WSDL/XSD) | Non implementato | — | Approccio contract-last corrente |
| Messaggistica asincrona (JMS/ActiveMQ) | Non implementata | — | `springline2-jms` commentato nel pom |
| Test di integrazione end-to-end | Non implementati | — | Solo unit test con mock (124 test) |
| Multi-tenancy (più enti su stessa istanza) | Non valutata | — | Futura decisione architetturale |
| Rate limiting per SIL | Non implementato | — | Potrebbe essere necessario con più enti |

---

## 19. Prossimi passi — Fasi Future

> **Nota**: Le fasi elencate qui sono allineate con `docs/guidelines/Plan.md`, che è il
> documento di riferimento autoritativo per il piano di implementazione.

### Riepilogo fasi completate

| Fase | Stato | Descrizione |
|------|-------|-------------|
| Fase 1 | ✅ | Fondazioni: pulizia demo, struttura middleware, OAuth2 |
| Fase 2 | ✅ | Resilienza, gestione errori, health check, test unitari |
| Fase 3 | ✅ | Persistenza PostgreSQL (plumbing): DataSource HikariCP + Jdbi |
| Fase 4 | ✅ | Semplificazione configurazione: eliminazione profili, YAML→Properties |
| Fase 5 | ✅ | Registro path-prefix, configurazione backend, `ProxyForwardingClient` |
| Fase 6 | ✅ | Schema DB: tabelle `mwpay_ente_config` e `mwpay_transaction_log`, DAO Jdbi, cache TTL |
| Fase 7 | ✅ | Logica di routing: `RoutingDecisionService`, eccezioni, refactoring endpoint |
| Fase 9 | ✅ | Log transazionale, metriche Micrometer, health check enti configurati, 124 test |

### Fase 8 — Endpoint SOAP Completi ⬜ (bloccata)

**Obiettivo**: Aggiungere tutti gli endpoint SOAP per mypay e mypivot.

**Prerequisiti**: censimento endpoint dai team mypay/mypivot (TBD — in attesa di risposta).

---

## 20. Agenti OpenCode

Il progetto utilizza **OpenCode** come strumento di sviluppo assistito da AI. Gli agenti personalizzati sono definiti in `.opencode/agents/` e le regole globali per tutti gli agenti si trovano in `AGENTS.md` nella root del repository.

### Agenti disponibili

| Agente | File | Quando usarlo |
|--------|------|---------------|
| `@expert` | `.opencode/agents/expert.md` | Decisioni architetturali, code review, debugging complesso |
| `@planner` | `.opencode/agents/planner.md` | Pianificare nuove fasi, aggiornare docs/, allineare Plan.md |
| `@tester` | `.opencode/agents/tester.md` | Scrivere test unitari Java, test integrazione, gestire Postman |
| `@orchestrator` | `.opencode/agents/orchestrator.md` | Gestire ecosistema AI: agenti, skill, comandi |

### Come invocare un agente

In OpenCode, gli agenti si invocano con `@nome-agente` nella chat. Ad esempio:

```
@planner aggiorna Plan.md con le modifiche della Fase 2
@planner pianifica la Fase 3 — logica di business
```

### Regole globali (`AGENTS.md`)

Il file `AGENTS.md` nella root del progetto definisce:
- Struttura del repository e contesto del progetto
- Vincoli tecnici obbligatori (profili, datasource, sicurezza XXE)
- Comandi di build e test per ambiente Windows/WSL
- Convenzioni di documentazione (italiano, versioning semantico)

---

## Appendice — Glossario

| Termine | Significato |
|---------|-------------|
| **SIL** | Sistemi Informativi Locali — i sistemi degli enti pubblici (comuni, province, ecc.) che inviano richieste al middleware |
| **Piattaforma Unitaria** | Sistema di pagoPA che gestisce i pagamenti elettronici degli enti pubblici |
| **pagoPA** | Piattaforma nazionale per i pagamenti verso la Pubblica Amministrazione |
| **OAuth2 Client Credentials** | Flusso OAuth2 machine-to-machine senza interazione utente: il client si autentica con `client_id` e `client_secret` e ottiene un token JWT |
| **SOAP** | Simple Object Access Protocol — protocollo per lo scambio di messaggi XML tra sistemi |
| **Spring WS** | Modulo Spring per la creazione di endpoint SOAP server-side |
| **SpringLine2** | Framework proprietario ARIA S.p.A. che estende Spring Boot con componenti per sicurezza, logging e configurazione |
| **Circuit Breaker** | Pattern di resilienza: interrompe le chiamate a un servizio non disponibile per evitare cascate di errori |
| **Resilience4j** | Libreria Java per i pattern di resilienza (Circuit Breaker, Retry, Rate Limiter, ecc.) |
| **Actuator** | Modulo Spring Boot che espone endpoint HTTP per il monitoraggio dell'applicazione |
| **XXE** | XML External Entity — categoria di attacchi che sfruttano il parser XML per leggere file locali o fare richieste di rete |
| **Contract-last** | Approccio SOAP in cui il contratto (WSDL) viene generato dal codice; opposto di contract-first |
| **Contract-first** | Approccio SOAP in cui il codice viene generato dal contratto (WSDL/XSD) |
| **codIpaEnte** | Codice IPA dell'ente pubblico — identificativo univoco dell'ente nel sistema |
| **tipoFlusso** | Tipo di flusso di tesoreria (`O` = Ordinario, `F` = Finanziario) |
