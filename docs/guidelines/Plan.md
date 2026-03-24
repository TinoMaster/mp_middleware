# Piano di Implementazione - Middleware MyPay (Gateway di Routing SOAP)

## Contesto

Il progetto è stato generato dall'archetype **SpringLine2** (ARIA S.p.A.) con codice demo
(Car CRUD, FileIO, JWT, Example). È stato trasformato in un **gateway di routing SOAP** che:

1. Riceve richieste SOAP dai SIL (Sistemi Informativi Locali) degli enti pubblici
2. Determina la **destinazione** (mypay o mypivot) in base al path della richiesta,
   consultando un **registro configurabile di path-prefix ↔ backend**
3. Determina la **modalità di instradamento** (Piattaforma Unitaria con OAuth2 oppure backend
   legacy in forward diretto) consultando il database per ente e tipo operazione
4. Instrada la richiesta verso il backend corretto

### Modello di routing a due dimensioni

```
SIL (richiesta SOAP)
     │
     ▼
MIDDLEWARE
     │
     ├─ [1] ROUTING PER PATH (configurabile — registro path-prefix ↔ backend)
     │       /ws/pivot/*  → destinazione: MYPIVOT
     │       /ws/pa/*     → destinazione: MYPAY
     │       /ws/fesp/*   → destinazione: MYPAY
     │       [nuovi path] → configurabili senza modificare il codice
     │
     └─ [2] ROUTING PER MODALITÀ (dinamico — dal DB, per ente e operazione)
             codIpaEnte → PIATTAFORMA_UNITARIA → forward con OAuth2 verso PU
             codIpaEnte → LEGACY               → forward diretto verso mypivot o mypay
```

> **Nota importante**: Una stessa piattaforma backend può avere **più prefissi di path**.
> Ad esempio, mypay gestisce sia `/ws/pa/*` che `/ws/fesp/*`. Il mapping path → backend
> è definito in configurazione (`application.properties`), non hardcoded nel codice.

### Decisioni architetturali consolidate (Marzo 2026)

| Decisione | Scelta |
|-----------|--------|
| Distinzione mypay / mypivot | Per path-prefix configurabile (un backend può avere più prefissi) |
| Path esposti al SIL | Identici ai backend originali — i SIL non cambiano nulla |
| URL backend mypay/mypivot | Placeholder configurabili (URL da definire in fase di deploy) |
| Ente non censito nel DB | SOAP Fault esplicito (`ENTE_NON_AUTORIZZATO`) |
| Auth verso mypay/mypivot (modalità legacy) | Nessuna — forward trasparente as-is |
| Migrazione enti alla PU | Manuale nel DB — nessuna migrazione automatica |

---

## Stato Attuale delle Fasi

| Fase | Stato | Descrizione |
|------|-------|-------------|
| Fase 1 | ✅ Completata | Fondazioni: pulizia demo, struttura middleware, OAuth2 |
| Fase 2 | ✅ Completata | Resilienza, gestione errori, health check, test unitari |
| Fase 3 | ✅ Completata | Persistenza PostgreSQL (plumbing): DataSource HikariCP + Jdbi configurati |
| Fase 4 | ✅ Completata | Semplificazione configurazione: eliminazione profili, YAML→Properties |
| Fase 4b | ✅ Completata | Logging e utility comuni: marker centralizzati, `logback-spring.xml`, `Constants`/`Utilities`, `JdbiSqlLogger` |
| Fase 5 | ✅ Completata | Registro path-prefix, configurazione backend, ProxyForwardingClient |
| Fase 6 | ✅ Completata | Schema DB: tabella configurazione enti e routing |
| Fase 7 | ✅ Completata | Logica di routing: `RoutingDecisionService`, eccezioni, refactoring endpoint |
| Fase 8 | ⬜ Da fare (bloccata) | Endpoint SOAP completi — richiede censimento endpoint dai team backend |
| Fase 9 | ✅ Completata | Log transazionale, audit, metriche |
| Fase 10 | ✅ Completata | Refactoring multi-ente: credenziali OAuth2 per-ente, schema `mygov_ente_config_pu`, test Java eliminati |

---

## Fase 1 - Fondazioni ✅

**Obiettivo**: Creare la struttura base del middleware senza logica di business, senza DB,
senza test.

### 1.1 Pulizia codice demo ✅

Eliminati tutti i file demo non pertinenti:

| Tipo | File eliminati |
|------|----------------|
| Controller | `CarController`, `ExampleController`, `FileIOController`, `JwtController`, `PublicApiController` |
| Service | `CarService`, `ExampleService`, `FileStorageService` |
| Domain | `Car` |
| Repository | `CarRepository` |
| SQL scripts | 4 script (tabella CAR e sequence) |

### 1.2 Struttura pacchetti creata ✅

```
it.ariaspa.mypay.mypaycore.api/
├── Application.java
├── config/
│   ├── PiattaformaUnitariaConfig.java       (@ConfigurationProperties OAuth2 e URL PU)
│   └── SoapWebServiceConfig.java            (@EnableWs + MessageDispatcherServlet)
├── auth/
│   ├── dto/OAuthTokenResponse.java
│   ├── OAuthTokenService.java               (OAuth2 Client Credentials, cache in-memory, ReentrantLock)
│   └── OAuthTokenInterceptor.java
├── soap/
│   ├── endpoint/
│   │   └── ReconciliationEndpoint.java      (PROTOTIPO — path da aggiornare in Fase 5)
│   └── exception/
│       └── SoapFaultExceptionResolver.java  [Fase 2]
├── client/
│   └── PiattaformaUnitariaClient.java       (RestTemplate + OAuth interceptor + retry 401)
├── common/exception/
│   ├── PiattaformaAuthenticationException.java
│   └── PiattaformaCommunicationException.java  [Fase 2]
└── health/                                  [Fase 2]
    ├── OAuthTokenHealthIndicator.java
    └── PiattaformaUnitariaHealthIndicator.java
```

### 1.3 Dipendenze aggiunte ✅

| Dipendenza | Motivo |
|-----------|--------|
| `spring-boot-starter-web-services` | Server SOAP (Spring WS @Endpoint) |
| `jakarta.xml.bind-api` | Marshalling/unmarshalling XML |
| `jaxb-runtime` | Implementazione JAXB per Jakarta |

### 1.4 Configurazione ✅

- `application.properties` riscritto: rimossa config demo, aggiunto blocco `piattaforma-unitaria`,
  sicurezza per endpoint SOAP, DataSource auto-config disabilitata
- `mypay.mypaycore-db/`: script SQL demo eliminati, creato `000_PLACEHOLDER.sql`

### 1.5 Documentazione ✅

- `docs/architettura/ARCHITETTURA_MIDDLEWARE.md` — documento architetturale in italiano

> **Nota**: Il path attuale dell'endpoint prototipo (`/pu/sil/soap/reconciliation/...`)
> è provvisorio e verrà sostituito in **Fase 5** con i path definitivi che replicano
> i path dei backend originali.

---

## Fase 2 - Resilienza, Error Handling, Health e Test ✅

**Obiettivo**: Rendere il middleware robusto, monitorabile e testato.

### 2.1 Resilienza (Resilience4j) ✅

**Dipendenze aggiunte**: `resilience4j-spring-boot3:2.2.0`, `spring-boot-starter-aop`

**Configurazione Circuit Breaker** (`piattaforma-unitaria-cb`):
- Sliding window: 10 chiamate
- Soglia fallimento: 50%
- Attesa in stato aperto: 30s
- Chiamate permesse in half-open: 3
- Eccezioni registrate: `PiattaformaCommunicationException`, `RestClientException`

**Configurazione Retry** (`piattaforma-unitaria-retry`):
- Tentativi massimi: 3
- Attesa iniziale: 1s
- Moltiplicatore esponenziale: 2x
- Eccezioni retry: `PiattaformaCommunicationException`

**Applicazione**: `@CircuitBreaker` e `@Retry` su `PiattaformaUnitariaClient.forwardSoapRequest()`
con metodo fallback.

### 2.2 Gestione Errori ✅

- **`SoapFaultExceptionResolver`**: Mappa eccezioni → SOAP Fault con codici:
  - `AUTH_ERROR` → `PiattaformaAuthenticationException`
  - `COMM_ERROR` → `PiattaformaCommunicationException`
  - `INTERNAL_ERROR` → eccezioni generiche
- **Timeouts**: OAuthTokenService (connect 5s, read 10s), PiattaformaUnitariaClient (connect 5s, read 30s)
- **Sicurezza XXE**: `ReconciliationEndpoint` hardened con `DocumentBuilderFactory` e
  `TransformerFactory` sicuri (DTD/external entities/XInclude disabilitati)

### 2.3 Health Check (Spring Actuator) ✅

| Health Indicator | Cosa verifica |
|-----------------|---------------|
| `OAuthTokenHealthIndicator` | Token OAuth2 in cache valido (non scaduto) |
| `PiattaformaUnitariaHealthIndicator` | Connettività verso la PU (HTTP GET leggero) |

Endpoint esposti: `/actuator/health`, `/actuator/info`, `/actuator/metrics`,
`/actuator/circuitbreakers`, `/actuator/retries`

### 2.4 Profili Multi-Ambiente ✅

> **Nota**: I profili `uat` e `prod` sono stati successivamente rimossi nella Fase 4.
> Solo `dev` è attivo.

| Profilo | Logging | Resilienza | Stato |
|---------|---------|-----------|-------|
| `dev` | DEBUG | Rilassata (soglia 80%, attesa 10s) | **Attivo** |
| `uat` | INFO | Standard | Da creare |
| `prod` | WARN | Conservativa (soglia 40%, attesa 60s) | Da creare |

### 2.5 Test Unitari ✅

| Classe di test | # Test | Copertura |
|---------------|--------|-----------|
| `OAuthTokenServiceTest` | 9 | Cache, refresh, gestione errori OAuth2 |
| `PiattaformaUnitariaClientTest` | 7 | Inoltro, retry 401, errori HTTP, fallback |
| `ReconciliationEndpointTest` | 6 | Proxy trasparente, body extraction, namespace |
| **Totale** | **22** | **BUILD SUCCESS, 0 fallimenti** |

---

## Fase 3 - Persistenza Database ✅ (Plumbing completato)

**Obiettivo**: Configurare la connessione al database PostgreSQL e predisporre il layer JDBC/Jdbi.

### Attività completate ✅

1. Driver `postgresql` + `spring-boot-starter-jdbc` + Jdbi (`jdbi3-spring5`, `jdbi3-sqlobject`,
   `jdbi3-stringtemplate4`) aggiunti al `pom.xml`
2. `DataSourceConfiguration.java` — configurazione manuale HikariCP + `DataSourceTransactionManager`
   (`@Primary`), prefisso `spring.datasource.pa.*`
3. `JdbiConfiguration.java` — istanza `jdbiPa`, plugin Jdbi, row mapper, SQL Object support
4. Blocco `spring.datasource.pa.*` con credenziali PostgreSQL nel profilo `dev`

### Lavoro rimanente (schema applicativo) ⬜

Rimandato alla **Fase 6** — vedi sezione dedicata.

---

## Fase 4 - Semplificazione Configurazione ✅

**Data**: Marzo 2026
**Risultato**: `mvn compile` → BUILD SUCCESS | `mvn test` → 22 test, 0 fallimenti

### Attività completate ✅

- Rimossi `application-uat.yml` e `application-prod.yml`; unico profilo: `dev`
- Tutti i file `.yml` convertiti in `.properties`:

| File eliminato (YAML) | File creato (Properties) | Modulo |
|-----------------------|--------------------------|--------|
| `application.yml` | `application.properties` | `mypay.mypaycore-springboot` |
| `application-dev.yml` | `application-dev.properties` | `mypay.mypaycore-springboot` |
| `application-uat.yml` | *(eliminato senza sostituzione)* | `mypay.mypaycore-springboot` |
| `bootstrap.yml` | `bootstrap.properties` | `mypay.mypaycore-springboot` |
| `config/application.yml` (test) | `config/application.properties` (test) | `mypay.mypaycore-springboot` |
| `application.yml` | `application.properties` | `mypay.mypaycore-properties` |
| `bootstrap.yml` | `bootstrap.properties` | `mypay.mypaycore-properties` |

- Prefisso datasource `spring.datasource.pa.*` verificato e allineato
- `startup.sh` aggiornato con `--spring.profiles.active=dev`
- `AGENTS.md` aggiornato con tabella profili corretta

---

## Fase 4b - Infrastruttura Logging e Utility Comuni ✅

**Data**: Marzo 2026

### Attività completate ✅

- Aggiunta la classe `LogMarker.java` per centralizzare i marker SLF4J del progetto
- Aggiunta la classe `LogHelper.java` per formattare firme metodo leggibili nei log tecnici
- Inserito `logback-spring.xml` nel modulo `mypay.mypaycore-properties` per la scrittura dei log su file separati (`backend`, `mon`, `app`, `audit`, `filter`)
- Predisposte le classi `Constants.java` e `Utilities.java` come contenitori condivisi rispettivamente per costanti e helper riusabili
- Attivato `JdbiSqlLogger` nella configurazione Jdbi per il tracciamento delle query SQL

---

## Fase 5 - Registro Path-Prefix, Configurazione Backend e ProxyForwardingClient ✅

**Data**: Marzo 2026
**Risultato**: `mvn test` → 43 test, 0 fallimenti, BUILD SUCCESS

**Obiettivo**: Predisporre l'infrastruttura di routing per path e il client di forward
trasparente verso i backend legacy (mypay e mypivot).

### 5.1 Registro path-prefix ↔ backend (configurabile) ✅

**Problema**: Una piattaforma backend può gestire **più prefissi di path**. Ad esempio,
mypay gestisce sia `/ws/pa/*` che `/ws/fesp/*`. In futuro potrebbero aggiungersi altri
prefissi. Il mapping non può essere hardcoded.

**Soluzione**: `PathRegistryConfig` — classe `@ConfigurationProperties(prefix = "routing")`
che carica il mapping in una `Map<String, String>` e offre un metodo
`resolveBackend(String requestPath) → Optional<BackendDestinatario>`.

**Componente creato**: `config/PathRegistryConfig.java`
- Enum interno `BackendDestinatario` con valori `MYPAY`, `MYPIVOT`
- Conversione automatica chiave normalizzata → path reale (`ws-pivot` → `/ws/pivot`)
- Algoritmo di risoluzione con longest-prefix matching
- Validazione `@PostConstruct`: errore se il path-map è vuoto

**Proprietà configurate**:
```properties
routing.path-map.ws-pivot=MYPIVOT
routing.path-map.ws-pa=MYPAY
routing.path-map.ws-fesp=MYPAY
```

> **Nota**: Le chiavi di properties non accettano `/` come separatore; si usa `-` come
> normalizzazione (es. `ws/pivot` → `ws-pivot`). Il codice riconvertirà al path reale.

### 5.2 Nuova configurazione `BackendRoutingConfig` ✅

**Componente creato**: `config/BackendRoutingConfig.java`

Classe `@Configuration` + `@ConfigurationProperties(prefix = "backend")` con:
- `backend.mypivot.base-url` — URL base mypivot
- `backend.mypay.base-url` — URL base mypay
- Metodo `getBaseUrlFor(BackendDestinatario)` per ottenere l'URL dal tipo di backend

**Proprietà configurate**:
```properties
backend.mypivot.base-url=${BACKEND_MYPIVOT_URL:http://localhost:8081}
backend.mypay.base-url=${BACKEND_MYPAY_URL:http://localhost:8082}
```

### 5.3 Aggiornamento configurazione SOAP server ✅

**File modificato**: `SoapWebServiceConfig.java`

Il `MessageDispatcherServlet` è stato registrato su `/ws/*` (precedentemente `/pu/sil/soap/*`).
Questo intercetta tutti i path che i SIL inviano ai backend (`/ws/pivot/*`, `/ws/pa/*`,
`/ws/fesp/*`).

### 5.4 `ProxyForwardingClient` ✅

**Componente creato**: `client/ProxyForwardingClient.java`

Client HTTP per il forward trasparente verso mypay/mypivot in modalità legacy:
- **Nessun token OAuth2** — le credenziali SIL (`codIpaEnte` + `password`) viaggiano as-is
- `RestTemplate` separato, senza `OAuthTokenInterceptor`
- Nessuna trasformazione del payload
- Timeout: connect 5s, read 30s
- `@CircuitBreaker(name = "backendLegacy")` + `@Retry(name = "backendLegacy")` con configurazione dedicata
- Metodo fallback per circuit breaker aperto

### 5.5 Aggiornamento endpoint prototipo ✅

Il `ReconciliationEndpoint` è stato aggiornato solo nel Javadoc: path e TODO per Phase 7.
Il codice resta invariato poiché Spring WS usa `@PayloadRoot` (namespace + localPart)
per il routing, non il path HTTP. L'endpoint risponde automaticamente su `/ws/*` dopo
il cambio del servlet path. Il refactoring con `RoutingDecisionService` è rimandato alla
Fase 7.

### 5.6 Proprietà Resilience4j `backendLegacy` ✅

Configurazione aggiunta in tutti i file di properties:
- Circuit breaker `backendLegacy`: finestra 10, soglia 50%, attesa 30s, 3 chiamate half-open
- Retry `backendLegacy`: 3 tentativi, 1s attesa, backoff esponenziale 2x
- Eccezioni registrate: `PiattaformaCommunicationException`, `RestClientException`

### 5.7 Aggiornamento sicurezza SpringLine2 ✅

Le `uri-matchers` di `spl.security.authentication` sono state aggiornate da
`/pu/sil/soap/**` a `/ws/**` in `application.properties` e `application-dev.properties`.

### 5.8 Bug fix: concatenazione in `application-dev.properties` ✅

Corretto un bug di concatenazione alla riga 34 dove due proprietà erano unite
sulla stessa riga (`...HikariPool-PA-devpiattaforma-unitaria.base-url=https://...`).
Aggiunto il newline mancante.

### 5.9 Test Fase 5 ✅

| Classe di test | # Test | Stato | Note |
|---------------|--------|-------|------|
| `PathRegistryConfigTest` | 12 | ✅ Nuova | Init validation, resolve backend, longest-prefix, edge cases |
| `BackendRoutingConfigTest` | 3 | ✅ Nuova | URL resolution per MYPAY e MYPIVOT, backend non riconosciuto |
| `ProxyForwardingClientTest` | 6 | ✅ Nuova | Forward success, errori HTTP, timeout, fallback circuit breaker |
| `OAuthTokenServiceTest` | 9 | ✅ Invariata | |
| `PiattaformaUnitariaClientTest` | 7 | ✅ Invariata | |
| `ReconciliationEndpointTest` | 6 | ✅ Invariata | |
| **Totale** | **43** | **BUILD SUCCESS, 0 fallimenti** | |

### Attività Fase 5 — Riepilogo

| # | Attività | File | Stato |
|---|---------|------|-------|
| 5.1 | `PathRegistryConfig` con enum `BackendDestinatario` | `config/PathRegistryConfig.java` (nuovo) | ✅ |
| 5.2 | `BackendRoutingConfig` con URL backend | `config/BackendRoutingConfig.java` (nuovo) | ✅ |
| 5.3 | Proprietà `routing.path-map.*` e `backend.*` | `application.properties`, `application-dev.properties`, test properties | ✅ |
| 5.4 | `SoapWebServiceConfig` servlet path `/ws/*` | `config/SoapWebServiceConfig.java` | ✅ |
| 5.5 | `ProxyForwardingClient` | `client/ProxyForwardingClient.java` (nuovo) | ✅ |
| 5.6 | Javadoc `ReconciliationEndpoint` | `soap/endpoint/ReconciliationEndpoint.java` | ✅ |
| 5.7 | 21 nuovi test (3 classi) | `PathRegistryConfigTest`, `BackendRoutingConfigTest`, `ProxyForwardingClientTest` | ✅ |
| 5.8 | Bug fix concatenazione properties | `application-dev.properties` | ✅ |
| 5.9 | Sicurezza uri-matchers → `/ws/**` | `application.properties`, `application-dev.properties` | ✅ |

### Decisioni confermate

- ✅ Path esposti: identici ai backend originali — `/ws/pivot/*`, `/ws/pa/*`, `/ws/fesp/*`
- ✅ Mapping path → backend: configurabile in `application.properties`
- ✅ Forward legacy: nessuna auth aggiuntiva, `RestTemplate` separato senza OAuth2
- ✅ URL backend: placeholder configurabili via variabili d'ambiente

---

## Fase 6 - Schema DB e Tabella Configurazione Enti ✅

**Obiettivo**: Definire lo schema del database applicativo del middleware, con priorità alla
tabella di configurazione enti che abilita il routing dinamico (PU o legacy).

**Prerequisito**: Fase 5 completata.

### 6.1 Tabella `MWPAY_ENTE_CONFIG` (routing per modalità)

Tabella centrale del sistema di routing. Per ogni ente e tipo operazione, indica se la
richiesta va instradata verso la Piattaforma Unitaria o verso il backend legacy.

```sql
CREATE TABLE mwpay_ente_config (
    id                 BIGSERIAL PRIMARY KEY,
    cod_ipa_ente       VARCHAR(50)  NOT NULL,
    tipo_operazione    VARCHAR(100) NOT NULL,  -- es. 'pivotSILAutorizzaImportFlussoTesoreria'
    modalita_routing   VARCHAR(30)  NOT NULL,  -- 'PIATTAFORMA_UNITARIA' | 'LEGACY'
    attivo             BOOLEAN      NOT NULL DEFAULT TRUE,
    note               TEXT,
    data_creazione     TIMESTAMP    NOT NULL DEFAULT NOW(),
    data_aggiornamento TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ente_operazione UNIQUE (cod_ipa_ente, tipo_operazione)
);
```

**Naming convention**: prefisso `mwpay_` per tutte le tabelle del middleware.

**Valori `modalita_routing`**:
- `PIATTAFORMA_UNITARIA` — inoltra con OAuth2 verso PU
- `LEGACY` — forward diretto verso mypay o mypivot

**Nota sulla migrazione enti**: la tabella viene popolata manualmente dagli amministratori.
Non esiste migrazione automatica. Un ente non presente equivale a ente non censito → SOAP Fault.

### 6.2 Tabella `MWPAY_TRANSACTION_LOG` (log transazioni)

```sql
CREATE TABLE mwpay_transaction_log (
    id                   BIGSERIAL PRIMARY KEY,
    cod_ipa_ente         VARCHAR(50)  NOT NULL,
    tipo_operazione      VARCHAR(100) NOT NULL,
    modalita_routing     VARCHAR(30)  NOT NULL,
    destinazione         VARCHAR(30)  NOT NULL,  -- 'MYPAY' | 'MYPIVOT'
    path_richiesta       VARCHAR(500) NOT NULL,
    http_status_risposta INTEGER,
    esito                VARCHAR(20)  NOT NULL,  -- 'OK' | 'ERRORE'
    messaggio_errore     TEXT,
    durata_ms            BIGINT,
    timestamp_richiesta  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 6.3 DAO Jdbi

**File da creare** (in `it.ariaspa.mypay.mypaycore.api`):
- `domain/EnteConfig.java` — modello Java tabella `mwpay_ente_config`
- `domain/TransactionLog.java` — modello Java tabella `mwpay_transaction_log`
- `repository/EnteConfigRepository.java` — DAO Jdbi (`@SqlQuery`, `@SqlUpdate`)
- `repository/TransactionLogRepository.java` — DAO Jdbi

### 6.4 Script SQL

**File da creare** in `mypay.mypaycore-db/src/main/sql/`:
- `001_CREATE_MWPAY_ENTE_CONFIG.sql`
- `002_CREATE_MWPAY_TRANSACTION_LOG.sql`
- `003_INSERT_ENTE_CONFIG_EXAMPLE.sql` (dati di esempio per sviluppo)

### Decisioni già prese

- ✅ Naming tabelle: prefisso `mwpay_`
- ✅ Strategia migrazione: script manuali (no Flyway per ora)
- ✅ Ente non censito: SOAP Fault (non fallback automatico)
- ✅ Migrazione enti: manuale nel DB, per ente e per operazione

### Decisioni da prendere

- Lista completa delle `tipo_operazione` da supportare
- Strategia caching della tabella `mwpay_ente_config` (in-memory con TTL? sempre da DB?)

---

## Fase 7 - Logica di Routing (RoutingDecisionService) ✅

**Data**: Marzo 2026
**Risultato**: `mvn test` → 89 test, 0 fallimenti, BUILD SUCCESS

**Obiettivo**: Implementare il cervello del gateway — il servizio che, ricevuta una richiesta
SOAP, decide verso quale backend instradarla e con quale modalità.

**Prerequisiti**: Fase 5 (path stabili + `PathRegistryConfig`) + Fase 6 (tabella `mwpay_ente_config` e DAO).

### 7.1 Eccezioni di routing ✅

**File creati**:
- `common/exception/EnteNonCensitoException.java` — eccezione con campi `codIpaEnte` e
  `tipoOperazione`, lanciata quando un ente non è censito o non ha una regola di routing attiva
- `common/exception/PathNonRiconosciutoException.java` — eccezione con campo `requestPath`,
  lanciata quando il path HTTP non corrisponde a nessun backend configurato

### 7.2 Modello di routing ✅

**File creato**: `routing/RoutingDecision.java`

Classe immutabile che rappresenta il risultato della decisione di routing:
- `destinazione` (`BackendDestinatario`) — MYPAY o MYPIVOT
- `modalita` (`ModalitaRouting`) — PIATTAFORMA_UNITARIA o LEGACY
- `urlBackend` (`String`) — URL base del backend di destinazione
- Metodi di convenienza: `isPiattaformaUnitaria()`, `isLegacy()`

### 7.3 `RoutingDecisionService` — il cervello del routing ✅

**File creato**: `routing/RoutingDecisionService.java`

Servizio `@Service` che implementa il routing a due dimensioni:

```
Input: codIpaEnte, tipoOperazione, pathRichiesta

1. Risolvere il backend di destinazione dal path (PathRegistryConfig)
   → se non trovato: PathNonRiconosciutoException → SOAP Fault PATH_NON_RICONOSCIUTO

2. Cercare la configurazione dell'ente nel DB/cache (EnteConfigCacheService)
   → se non trovato: EnteNonCensitoException → SOAP Fault ENTE_NON_AUTORIZZATO

3. Comporre la RoutingDecision con destinazione, modalita e URL backend

Output: RoutingDecision { destinazione, modalita, urlBackend }
```

Dipendenze iniettate: `PathRegistryConfig`, `EnteConfigCacheService`, `BackendRoutingConfig`.
Il servizio non esegue comunicazione HTTP — si limita a prendere la decisione.

### 7.4 Aggiornamento `SoapFaultExceptionResolver` ✅

**File modificato**: `soap/exception/SoapFaultExceptionResolver.java`

Nuovi mapping aggiunti:
- `EnteNonCensitoException` → SOAP Fault **Client** con codice `ENTE_NON_AUTORIZZATO`
- `PathNonRiconosciutoException` → SOAP Fault **Client** con codice `PATH_NON_RICONOSCIUTO`

Refactoring: metodo helper `addFaultDetailCode()` per ridurre duplicazione. Le eccezioni di
routing generano Fault Client (errore del chiamante), mentre le eccezioni di comunicazione
generano Fault Server (errore del middleware/backend).

### 7.5 Refactoring `ReconciliationEndpoint` ✅

**File modificato**: `soap/endpoint/ReconciliationEndpoint.java`

Refactoring completo per integrare il routing dinamico:

1. **Nuove dipendenze**: `ProxyForwardingClient`, `RoutingDecisionService` (oltre al preesistente
   `PiattaformaUnitariaClient`)
2. **Estrazione `codIpaEnte`**: parsifica l'intero SOAP Envelope, cerca `<codIpaEnte>` nell'Header
   (prima namespace-agnostic, poi namespace-specific)
3. **Estrazione path HTTP**: usa `TransportContextHolder` → `HttpServletConnection` →
   `HttpServletRequest.getRequestURI()`
4. **Routing**: chiama `RoutingDecisionService.decide()` e usa il client appropriato:
   - `PIATTAFORMA_UNITARIA` → `PiattaformaUnitariaClient.forwardSoapRequest()`
   - `LEGACY` → `ProxyForwardingClient.forwardToLegacyBackend()`
5. Le eccezioni di routing vengono propagate al `SoapFaultExceptionResolver`

### 7.6 Test Fase 7 ✅

| Classe di test | # Test | Stato | Note |
|---------------|--------|-------|------|
| `RoutingDecisionServiceTest` | 13 | ✅ Nuova | 4 nested class: PU routing, legacy routing, error cases, dependency interactions |
| `ReconciliationEndpointTest` | 14 | ✅ Riscritta | 5 nested class: PU routing, legacy routing, error handling, codIpaEnte extraction, namespace |
| `SoapFaultExceptionResolverTest` | 8 | ✅ Nuova | Copre 5 tipi di eccezione + errore nel resolver |
| `PathRegistryConfigTest` | 12 | ✅ Invariata | |
| `BackendRoutingConfigTest` | 3 | ✅ Invariata | |
| `ProxyForwardingClientTest` | 6 | ✅ Invariata | |
| `EnteConfigCacheServiceTest` | 11 | ✅ Invariata | |
| `EnteConfigRowMapperTest` | 3 | ✅ Invariata | |
| `DomainModelTest` | 10 | ✅ Invariata | |
| `PiattaformaUnitariaClientTest` | 7 | ✅ Invariata | |
| `OAuthTokenServiceTest` | 9 | ✅ Invariata | |
| **Totale** | **89** | **BUILD SUCCESS, 0 fallimenti** | |

### Attività Fase 7 — Riepilogo

| # | Attività | File | Stato |
|---|---------|------|-------|
| 7.1 | `EnteNonCensitoException` | `common/exception/EnteNonCensitoException.java` (nuovo) | ✅ |
| 7.1b | `PathNonRiconosciutoException` | `common/exception/PathNonRiconosciutoException.java` (nuovo) | ✅ |
| 7.2 | `RoutingDecision` | `routing/RoutingDecision.java` (nuovo) | ✅ |
| 7.3 | `RoutingDecisionService` | `routing/RoutingDecisionService.java` (nuovo) | ✅ |
| 7.4 | `SoapFaultExceptionResolver` aggiornato | `soap/exception/SoapFaultExceptionResolver.java` | ✅ |
| 7.5 | `ReconciliationEndpoint` refactored | `soap/endpoint/ReconciliationEndpoint.java` | ✅ |
| 7.6 | `RoutingDecisionServiceTest` | 13 test (nuovo) | ✅ |
| 7.7 | `ReconciliationEndpointTest` riscritta | 14 test (riscritto) | ✅ |
| 7.8 | `SoapFaultExceptionResolverTest` | 8 test (nuovo) | ✅ |
| 7.9 | Compilazione e test | 89 test, 0 fallimenti, BUILD SUCCESS | ✅ |

### Decisioni confermate

- ✅ Ente non censito → SOAP Fault Client `ENTE_NON_AUTORIZZATO` (nessun fallback)
- ✅ Path non riconosciuto → SOAP Fault Client `PATH_NON_RICONOSCIUTO`
- ✅ Errori di routing → Client Fault; errori di comunicazione → Server Fault
- ✅ `codIpaEnte` estratto dall'Header SOAP (prima namespace-agnostic, poi namespace-specific)
- ✅ Path HTTP estratto dal `TransportContextHolder` di Spring WS

---

## Fase 8 - Endpoint SOAP Completi ⬜

**Obiettivo**: Aggiungere tutti gli endpoint SOAP necessari — sia quelli mypivot (`/ws/pivot/*`)
che quelli mypay (`/ws/pa/*`, `/ws/fesp/*`) — replicando i path esistenti nei backend originali.

**Prerequisiti**: Fase 5 (path stabili) + Fase 7 (routing operativo).

### 8.1 Censimento endpoint

Prima di implementare, è necessario identificare la lista completa degli endpoint:

| Backend | Path pattern | Operazioni richieste | Stato |
|---------|-------------|---------------------|-------|
| mypivot | `/ws/pivot/PagamentiTelematiciPagatiRiconciliati` | `pivotSILAutorizzaImportFlussoTesoreria` | Prototipo in Fase 1 |
| mypivot | `/ws/pivot/*` | TBD | Da censire |
| mypay | `/ws/pa/PagamentiTelematiciCCPPa` | TBD | Da censire |
| mypay | `/ws/pa/*` | TBD | Da censire |
| mypay | `/ws/fesp/*` | TBD | Da censire |

> **Azione richiesta**: richiedere ai team mypay e mypivot la lista completa degli endpoint
> SOAP esposti ai SIL (path, namespace, local part, struttura messaggi).

### 8.2 Strategia di implementazione

**Approccio consigliato**: mantenere l'approccio **contract-last** (attuale) per la semplicità,
con possibile migrazione a contract-first in futuro se i WSDL diventano disponibili.

Ogni endpoint sarà un `@Endpoint` Spring WS con:
- Namespace e local part corretti
- Protezione XXE (`DocumentBuilderFactory` hardened)
- Delega a `RoutingDecisionService` per il routing

### 8.3 Struttura pacchetti aggiornata

```
soap/
├── endpoint/
│   ├── mypivot/
│   │   ├── ReconciliationEndpoint.java      (già presente, da aggiornare)
│   │   └── [altri endpoint mypivot]
│   └── mypay/
│       └── [endpoint mypay — /ws/pa/* e /ws/fesp/*]
└── exception/
    └── SoapFaultExceptionResolver.java
```

### Decisioni da prendere

- Lista completa degli endpoint (TBD — da ricevere dai team mypay/mypivot)
- Migrazione contract-last → contract-first (WSDL/XSD) ora o in futuro?
- Validazione XML Schema sulle richieste in ingresso (opzionale ma raccomandato)

---

## Fase 9 - Log Transazionale, Audit e Metriche ✅

**Data**: Marzo 2026
**Risultato**: `mvn test` → 124 test, 0 fallimenti, BUILD SUCCESS

**Obiettivo**: Garantire osservabilità completa del gateway — ogni richiesta tracciata,
ogni errore registrato, metriche per operazione e per ente.

**Prerequisiti**: Fase 6 (tabelle DB) + Fase 7 (routing).

### 9.1 `TransactionLoggingService` ✅

**File creato**: `logging/TransactionLoggingService.java`

Servizio `@Service` che scrive un record in `mwpay_transaction_log` per ogni richiesta SOAP
processata dal middleware. Tre metodi principali:

- `logSuccesso(codIpaEnte, tipoOperazione, decision, pathRichiesta, httpStatus, durataMs)` —
  registra una transazione conclusa con successo
- `logErrore(codIpaEnte, tipoOperazione, decision, pathRichiesta, errore, durataMs)` —
  registra una transazione fallita (post-routing, quando la `RoutingDecision` è già stata presa)
- `logErrorePreRouting(codIpaEnte, tipoOperazione, pathRichiesta, errore, durataMs)` —
  registra un errore avvenuto prima della decisione di routing (ente non censito, path non
  riconosciuto), con `modalitaRouting="SCONOSCIUTA"` e `destinazione="SCONOSCIUTA"`

**Pattern di inserimento**: sincrono, after-request. Se il logging fallisce, **non blocca la
risposta al SIL** (try-catch silenzioso + log applicativo di warning). Il messaggio di errore
viene troncato a 4000 caratteri per rispettare il limite della colonna `TEXT`.

### 9.2 Integrazione nel `ReconciliationEndpoint` ✅

**File modificato**: `soap/endpoint/ReconciliationEndpoint.java`

Il metodo `handleReconciliationRequest()` è stato aggiornato per integrare logging e metriche:

1. Registra `startTime = Instant.now()` all'inizio
2. Su successo → `transactionLoggingService.logSuccesso()` + `metricsService.registraSuccesso()`
3. Su errore post-routing → `transactionLoggingService.logErrore()` + `metricsService.registraErrore()`
4. Su errore pre-routing (ente non censito, path non riconosciuto) →
   `transactionLoggingService.logErrorePreRouting()` + `metricsService.registraErrore()`

Nuove dipendenze nel costruttore: `TransactionLoggingService`, `MiddlewareMetricsService`.

### 9.3 `EnteConfigHealthIndicator` ✅

**File creato**: `health/EnteConfigHealthIndicator.java`

Health indicator Spring Boot Actuator che verifica la tabella `mwpay_ente_config`:
- **UP** se `EnteConfigCacheService.size() > 0` (almeno un ente configurato e attivo)
- **DOWN** se la cache è vuota o se si verifica un'eccezione durante il controllo
- Restituisce il numero di enti configurati come dettaglio

### 9.4 `MiddlewareMetricsService` ✅

**File creato**: `metrics/MiddlewareMetricsService.java`

Servizio `@Service` che espone metriche Micrometer tramite Spring Actuator:

| Metrica | Tipo | Tag | Descrizione |
|---------|------|-----|-------------|
| `middleware.richieste.totali` | Counter | `ente`, `operazione`, `modalita`, `esito` | Conteggio richieste per combinazione |
| `middleware.richieste.durata` | Timer | `operazione`, `modalita` | Distribuzione durata richieste |
| `middleware.enti.configurati` | Gauge | — | Numero di enti attivi nella configurazione |

Metodi principali:
- `registraSuccesso(codIpaEnte, tipoOperazione, modalita, durataMs)` — incrementa contatore
  con `esito=OK` e registra durata nel timer
- `registraErrore(codIpaEnte, tipoOperazione, modalita, durataMs)` — incrementa contatore
  con `esito=ERRORE` e registra durata nel timer

Gestione robusta: i parametri `null` vengono sostituiti con `"sconosciuto"`.

### 9.5 Test Fase 9 ✅

| Classe di test | # Test | Stato | Note |
|---------------|--------|-------|------|
| `TransactionLoggingServiceTest` | 11 | ✅ Nuova | 5 nested class: successo, errore, errore pre-routing, troncamento, resilienza DB |
| `EnteConfigHealthIndicatorTest` | 4 | ✅ Nuova | UP con enti, UP con 1 ente, DOWN cache vuota, DOWN eccezione |
| `MiddlewareMetricsServiceTest` | 12 | ✅ Nuova | 5 nested class: gauge, contatore successo, contatore errore, timer durata, nomi metriche |
| `ReconciliationEndpointTest` | 18 | ✅ Aggiornata | +3 test in nested class `LoggingEMetriche`, costruttore aggiornato a 5 parametri |
| `SoapFaultExceptionResolverTest` | 8 | ✅ Fix lenient() | Stub `addFaultDetail`/`addFaultDetailElement` resi `lenient()` |
| `RoutingDecisionServiceTest` | 13 | ✅ Invariata | |
| `DomainModelTest` | 10 | ✅ Invariata | |
| `EnteConfigCacheServiceTest` | 11 | ✅ Invariata | |
| `EnteConfigRowMapperTest` | 3 | ✅ Invariata | |
| `PathRegistryConfigTest` | 12 | ✅ Invariata | |
| `BackendRoutingConfigTest` | 3 | ✅ Invariata | |
| `ProxyForwardingClientTest` | 6 | ✅ Invariata | |
| `PiattaformaUnitariaClientTest` | 7 | ✅ Invariata | |
| `OAuthTokenServiceTest` | 9 | ✅ Invariata | |
| **Totale** | **124** | **BUILD SUCCESS, 0 fallimenti** | |

### Attività Fase 9 — Riepilogo

| # | Attività | File | Stato |
|---|---------|------|-------|
| 9.1 | `TransactionLoggingService` | `logging/TransactionLoggingService.java` (nuovo) | ✅ |
| 9.2 | `ReconciliationEndpoint` aggiornato | `soap/endpoint/ReconciliationEndpoint.java` | ✅ |
| 9.3 | `EnteConfigHealthIndicator` | `health/EnteConfigHealthIndicator.java` (nuovo) | ✅ |
| 9.4 | `MiddlewareMetricsService` | `metrics/MiddlewareMetricsService.java` (nuovo) | ✅ |
| 9.5 | `TransactionLoggingServiceTest` | 11 test (nuovo) | ✅ |
| 9.6 | `EnteConfigHealthIndicatorTest` | 4 test (nuovo) | ✅ |
| 9.7 | `MiddlewareMetricsServiceTest` | 12 test (nuovo) | ✅ |
| 9.8 | `ReconciliationEndpointTest` aggiornata | +3 test logging/metriche | ✅ |
| 9.9 | `SoapFaultExceptionResolverTest` fix | Stub `lenient()` per fault detail | ✅ |
| 9.10 | Compilazione e test | 124 test, 0 fallimenti, BUILD SUCCESS | ✅ |

### Decisioni confermate

- ✅ Log transazionale sincrono, after-request — se il logging fallisce, non blocca la risposta al SIL
- ✅ Messaggio errore troncato a 4000 caratteri per rispettare limiti colonna DB
- ✅ Metriche Micrometer con tag `ente`, `operazione`, `modalita`, `esito`
- ✅ Gauge `middleware.enti.configurati` collegato a `EnteConfigCacheService.size()`
- ✅ Health indicator DOWN se zero enti configurati o eccezione durante il controllo

---

## Fase 10 - Refactoring Multi-Ente (Credenziali OAuth2 per-ente) ✅

**Data**: 24 Marzo 2026  
**Risultato**: `mvn compile` → BUILD SUCCESS (42 source files, 0 errori). Test Java eliminati — testing via Postman E2E.

**Obiettivo**: Evolvere il middleware da un'architettura con credenziali OAuth2 globali a un'architettura **multi-ente** dove ogni ente pubblico ha le proprie credenziali OAuth2 (`client_id` e `client_secret`) memorizzate nel database. Il routing PU vs LEGACY dipende dalla **presenza/assenza** di una configurazione attiva per l'ente, non più da una colonna `modalita_routing` esplicita.

### Decisioni architetturali prese

| Decisione | Scelta |
|-----------|--------|
| Schema DB | Nuova tabella `mygov_ente_config_pu` (prefisso `mygov_` — parte del dominio PA) |
| Tabella enti | Usa `mygov_ente` esistente nel DB condiviso — non creata dal middleware |
| Credenziali OAuth2 | Per-ente in `mygov_ente_config_pu.client_id` / `.client_secret` |
| Logica routing | `EnteCompleto.isPiattaformaUnitaria()` — routing basato su presenza/assenza config PU |
| `tipoOperazione` nel routing | **Eliminata** — il routing è per-ente, non per-operazione |
| `OAuthTokenInterceptor` | **Eliminato** — il token Bearer viene aggiunto manualmente da `PiattaformaUnitariaClient` |
| Cache token | `ConcurrentHashMap<codIpaEnte, TokenData>` — una entry per ente |
| Cache enti | `EnteCacheService` con `ConcurrentHashMap<codIpaEnte, EnteCompleto>` |
| Test Java | **Eliminati** — testing esclusivamente via Postman E2E |
| Dipendenze test | `spring-boot-starter-test` e `spring-ws-test` rimossi dal `pom.xml` |

### Componenti eliminati

| Componente | Tipo | Motivo |
|-----------|------|--------|
| `EnteConfig.java` | Dominio | Sostituito da `Ente` + `EnteConfigPu` + `EnteCompleto` |
| `EnteConfigRepository.java` | DAO | Sostituito da `EnteRepository` + `EnteConfigPuRepository` |
| `EnteConfigRowMapper.java` | RowMapper | Sostituito da `EnteRowMapper` + `EnteConfigPuRowMapper` |
| `EnteConfigCacheService.java` | Cache | Sostituito da `EnteCacheService` |
| `OAuthTokenInterceptor.java` | Interceptor | Token Bearer aggiunto manualmente da `PiattaformaUnitariaClient` |
| `src/test/` (14 classi, 124 test) | Test | Testing spostato su Postman E2E |
| `001_CREATE_MWPAY_ENTE_CONFIG.sql` | SQL | Sostituito da `004_CREATE_MYGOV_ENTE_CONFIG_PU.sql` |
| `003_INSERT_ENTE_CONFIG_EXAMPLE.sql` | SQL | Sostituito da `006_INSERT_ENTE_CONFIG_PU_EXAMPLE.sql` |

### Componenti creati

| Componente | Tipo | Descrizione |
|-----------|------|-------------|
| `domain/Ente.java` | Dominio | Modello tabella `mygov_ente` (DB condiviso) |
| `domain/EnteConfigPu.java` | Dominio | Modello tabella `mygov_ente_config_pu` con `client_id`, `client_secret`, `attivo` |
| `domain/EnteCompleto.java` | Aggregato | Ente + EnteConfigPu; metodi: `isPiattaformaUnitaria()`, `getClientId()`, `getClientSecret()` |
| `repository/EnteRepository.java` | DAO | Jdbi per `mygov_ente`; LEFT JOIN con `mygov_ente_config_pu` |
| `repository/EnteConfigPuRepository.java` | DAO | Jdbi per `mygov_ente_config_pu` |
| `repository/EnteRowMapper.java` | RowMapper | Mapping ResultSet → `Ente` |
| `repository/EnteConfigPuRowMapper.java` | RowMapper | Mapping ResultSet → `EnteConfigPu` |
| `repository/EnteCacheService.java` | Cache | Cache TTL `ConcurrentHashMap<codIpaEnte, EnteCompleto>`; metodi: `findByCodIpaEnte()`, `size()`, `countEntiPiattaformaUnitaria()` |
| `004_CREATE_MYGOV_ENTE_CONFIG_PU.sql` | SQL | Crea tabella `mygov_ente_config_pu` |
| `005_DROP_MWPAY_ENTE_CONFIG.sql` | SQL | Rimuove la vecchia tabella (eseguire in migrazione) |
| `006_INSERT_ENTE_CONFIG_PU_EXAMPLE.sql` | SQL | Dati di esempio per dev |

### Componenti aggiornati

| Componente | Modifica |
|-----------|---------|
| `OAuthTokenService.java` | Multi-ente: `ConcurrentHashMap<codIpaEnte, TokenData>`; nuovi metodi `getAccessToken(codIpaEnte, clientId, clientSecret)`, `refreshToken(codIpaEnte, clientId, clientSecret)`, `isTokenValid(codIpaEnte)`, `getTokenCacheSize()`, `getEntiInCache()` |
| `PiattaformaUnitariaConfig.java` | Rimossi `clientId` e `clientSecret` dall'inner class `Auth` |
| `RoutingDecision.java` | Aggiunto campo `EnteCompleto ente`; costruttore: `(destinazione, modalita, urlBackend, ente)` |
| `RoutingDecisionService.java` | Firma: `decide(codIpaEnte, pathRichiesta)` (rimossa `tipoOperazione`); usa `EnteCacheService`; algoritmo 2 passi |
| `PiattaformaUnitariaClient.java` | Firma: `forwardSoapRequest(path, soapXml, ente)`; Bearer aggiunto manualmente |
| `ReconciliationEndpoint.java` | Chiama `routingDecisionService.decide(codIpaEnte, requestPath)` (2 param); chiama `piattaformaClient.forwardSoapRequest(path, xml, decision.getEnte())` |
| `EnteNonCensitoException.java` | Rimossa `tipoOperazione`; costruttore: `EnteNonCensitoException(codIpaEnte)` |
| `SoapFaultExceptionResolver.java` | Messaggio fault aggiornato (senza `tipoOperazione`) |
| `OAuthTokenHealthIndicator.java` | Itera su tutti gli enti in cache token; riporta `tokensValidi` e `tokensInCache` |
| `EnteConfigHealthIndicator.java` | Usa `EnteCacheService`; riporta `entiTotali`, `entiConPiattaformaUnitaria`, `entiLegacy` |
| `TransactionLoggingService.java` | Rimossa `tipoOperazione` dalle firme pubbliche; usa costante `"N/A"` internamente per compatibilità DB |
| `MiddlewareMetricsService.java` | Usa `EnteCacheService`; rimossa `tipoOperazione`; due gauge: `middleware.enti.totali` e `middleware.enti.piattaforma.unitaria` |
| `JdbiConfiguration.java` | Registra i nuovi RowMapper (`EnteRowMapper`, `EnteConfigPuRowMapper`) e repository |
| `application.properties` | Rimossi `piattaforma-unitaria.auth.client-id` e `.client-secret`; aggiornato commento cache |
| `application-dev.properties` | Rimossi `piattaforma-unitaria.auth.client-id` e `.client-secret` |
| `pom.xml` | Rimossi `spring-boot-starter-test` e `spring-ws-test` |

### Script SQL aggiornati

| Script | Stato | Scopo |
|--------|-------|-------|
| `000_PLACEHOLDER.sql` | Invariato | Placeholder vuoto |
| `002_CREATE_MWPAY_TRANSACTION_LOG.sql` | Invariato | Tabella log transazioni |
| `004_CREATE_MYGOV_ENTE_CONFIG_PU.sql` | ✅ Nuovo | Nuova tabella config PU per-ente |
| `005_DROP_MWPAY_ENTE_CONFIG.sql` | ✅ Nuovo | Rimozione vecchia tabella (eseguire in migrazione) |
| `006_INSERT_ENTE_CONFIG_PU_EXAMPLE.sql` | ✅ Nuovo | Dati di esempio per dev |
| `001_CREATE_MWPAY_ENTE_CONFIG.sql` | ❌ Rimosso | Sostituito da 004 + 005 |
| `003_INSERT_ENTE_CONFIG_EXAMPLE.sql` | ❌ Rimosso | Sostituito da 006 |

### Nuovo flusso (post refactoring)

```
SIL → POST /ws/pivot/...
  → ReconciliationEndpoint
    → estrae codIpaEnte dall'Header SOAP
    → EnteCacheService.findByCodIpaEnte(codIpaEnte)
      → query: mygov_ente LEFT JOIN mygov_ente_config_pu
      → non trovato? → EnteNonCensitoException → SOAP Fault ENTE_NON_AUTORIZZATO
    → PathRegistryConfig.resolveBackend(path) → MYPAY/MYPIVOT
    → EnteCompleto.isPiattaformaUnitaria()?
      → SÌ: PiattaformaUnitariaClient.forwardSoapRequest(path, xml, ente)
        → OAuthTokenService.getAccessToken(codIpaEnte, clientId, clientSecret) [cache per-ente]
      → NO: ProxyForwardingClient.forwardToLegacyBackend(dest, path, xml)
```

---

## Note Tecniche

### Compilazione (ambiente Windows/WSL)

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn compile -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

### Esecuzione test

> **Nota** (refactoring multi-ente, 24 Mar 2026): `mvn test` **non è più eseguibile**.
> La directory `src/test/` e le dipendenze `spring-boot-starter-test` e `spring-ws-test`
> sono state eliminate nel refactoring multi-ente. I 124 test JUnit 5 esistenti non esistono più.
> Il testing avviene esclusivamente via collection Postman E2E:
> `docs/procedures/GUIDA_TEST_POSTMAN_END_TO_END.md`.

### Vincoli noti

| Vincolo | Dettaglio |
|---------|-----------|
| Enforcer Maven | Richiede OS Unix — usare `-Denforcer.skip=true` su Windows |
| DataSource | Prefisso `spring.datasource.pa.*`, configurato manualmente in `DataSourceConfiguration.java` integrato con `JdbiConfiguration.java` |
| Spring WS | `springline2-ws` è client SOAP; server gestito da `spring-boot-starter-web-services` |
| Profilo `local` | Rimosso — non ricreare |
| `shutdown.pid` | Rimosso — non ricreare logica `ApplicationPidFileWriter` |
| File di configurazione | Formato `.properties` (la migrazione da `.yml` è completata) |
| Profilo attivo | Solo `dev` — i profili `uat` e `prod` vanno creati al momento del deploy |
| Path SOAP | Il servlet SOAP è su `/ws/*` (aggiornato dalla Fase 5); espone i path identici ai backend originali |
| Test Java | Eliminati nel refactoring multi-ente (24 Mar 2026) — `src/test/` rimossa, dipendenze test rimosse dal `pom.xml`. Testing via Postman E2E. |
| Credenziali OAuth2 | Non più globali — ogni ente ha il proprio `client_id` e `client_secret` in `mygov_ente_config_pu` |

### Struttura componenti (stato attuale post Fase 10)

```
it.ariaspa.mypay.mypaycore.api/
├── config/
│   ├── PiattaformaUnitariaConfig.java     (✅ Fase 1 — senza clientId/clientSecret globali)
│   ├── PathRegistryConfig.java            (✅ Fase 5 — mapping path-prefix → backend)
│   ├── BackendRoutingConfig.java          (✅ Fase 5 — URL backend)
│   ├── SoapWebServiceConfig.java          (✅ Fase 5 — servlet su /ws/*)
│   ├── DataSourceConfiguration.java       (✅ Fase 3)
│   └── JdbiConfiguration.java            (✅ Fase 3 — aggiornato Fase 10: nuovi RowMapper)
├── auth/
│   └── OAuthTokenService.java             (✅ Fase 10 — multi-ente: ConcurrentHashMap<codIpaEnte, TokenData>)
├── routing/
│   ├── RoutingDecision.java               (✅ Fase 10 — aggiunto campo EnteCompleto)
│   └── RoutingDecisionService.java        (✅ Fase 10 — firma: decide(codIpaEnte, pathRichiesta))
├── client/
│   ├── PiattaformaUnitariaClient.java     (✅ Fase 10 — firma: forwardSoapRequest(path, xml, ente))
│   └── ProxyForwardingClient.java         (✅ Fase 5)
├── soap/
│   ├── endpoint/
│   │   └── ReconciliationEndpoint.java    (✅ Fase 10 — aggiornato con nuove firme)
│   └── exception/
│       └── SoapFaultExceptionResolver.java (✅ Fase 10 — messaggio fault senza tipoOperazione)
├── domain/
│   ├── ModalitaRouting.java               (✅ Fase 6)
│   ├── Ente.java                          (✅ Fase 10 — modello mygov_ente)
│   ├── EnteConfigPu.java                  (✅ Fase 10 — modello mygov_ente_config_pu)
│   ├── EnteCompleto.java                  (✅ Fase 10 — aggregato: Ente + EnteConfigPu)
│   └── TransactionLog.java               (✅ Fase 6)
├── repository/
│   ├── EnteRepository.java                (✅ Fase 10 — DAO Jdbi per mygov_ente)
│   ├── EnteRowMapper.java                 (✅ Fase 10)
│   ├── EnteConfigPuRepository.java        (✅ Fase 10 — DAO Jdbi per mygov_ente_config_pu)
│   ├── EnteConfigPuRowMapper.java         (✅ Fase 10)
│   ├── EnteCacheService.java              (✅ Fase 10 — cache TTL ConcurrentHashMap<codIpaEnte, EnteCompleto>)
│   ├── TransactionLogRepository.java      (✅ Fase 6)
│   └── TransactionLogRowMapper.java       (✅ Fase 6)
├── common/exception/
│   ├── PiattaformaAuthenticationException.java  (✅ Fase 1)
│   ├── PiattaformaCommunicationException.java   (✅ Fase 2)
│   ├── EnteNonCensitoException.java             (✅ Fase 10 — senza tipoOperazione)
│   └── PathNonRiconosciutoException.java        (✅ Fase 7)
├── logging/
│   └── TransactionLoggingService.java     (✅ Fase 10 — firme senza tipoOperazione)
├── metrics/
│   └── MiddlewareMetricsService.java      (✅ Fase 10 — gauge enti.totali e enti.piattaforma.unitaria)
└── health/
    ├── OAuthTokenHealthIndicator.java            (✅ Fase 10 — itera su tutti gli enti in cache)
    ├── PiattaformaUnitariaHealthIndicator.java   (✅ Fase 2)
    └── EnteConfigHealthIndicator.java            (✅ Fase 10 — usa EnteCacheService)
```
