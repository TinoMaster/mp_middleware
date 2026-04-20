# mypay.mypaycore

Middleware di integrazione tra i **SIL** (Sistemi Informativi Locali) degli enti pubblici e la **Piattaforma Unitaria** di pagoPA.

- **Versione**: 1.0.0
- **Framework**: SpringLine2 (ARIA S.p.A.) — estensione proprietaria di Spring Boot 3.x
- **Linguaggio**: Java 17
- **Build**: Maven multi-modulo
- **Copyright**: Regione Lombardia
- **Distributore**: ARIA S.p.A.
- **Contatto**: configuration.management@ariaspa.it

---

## Indice

1. [Descrizione funzionale](#descrizione-funzionale)
2. [Architettura](#architettura)
3. [Struttura del repository](#struttura-del-repository)
4. [Prerequisiti](#prerequisiti)
5. [Configurazione database](#configurazione-database)
6. [Configurazione applicativa](#configurazione-applicativa)
7. [Variabili d'ambiente](#variabili-dambiente)
8. [Compilazione](#compilazione)
9. [Configurazione IntelliJ IDEA](#configurazione-intellij-idea)
10. [Esecuzione](#esecuzione)
11. [Endpoint esposti](#endpoint-esposti)
12. [Resilienza](#resilienza)
13. [Monitoraggio e Health Check](#monitoraggio-e-health-check)
14. [Profili applicativi](#profili-applicativi)
15. [Struttura database](#struttura-database)

---

## Descrizione funzionale

Il middleware si interpone tra i SIL degli enti pubblici lombardi e la Piattaforma Unitaria di pagoPA:

- Espone **40 operazioni SOAP** distribuite su **10 endpoint** ai SIL (9 MyPay + 1 MyPivot)
- Gestisce autonomamente l'**autenticazione OAuth2** (Client Credentials) verso pagoPA
- Inoltra le richieste autenticate e restituisce le risposte
- Supporta **routing dinamico per ente** (DB-driven) con cache duale (`codIpaEnte` + `codiceFiscale`)
- Supporta **upload di flussi** tramite proxy REST (max 100 MB)
- Include **circuit breaker e retry** con backoff esponenziale (Resilience4j)
- Registra ogni transazione su database per audit (`mygov_mw_transaction_log`)

### Flusso di routing

Il routing è bidimensionale:

1. **Per path**: `/ws/pivot/*` → MYPIVOT, `/ws/pa/*` → MYPAY, `/ws/fesp/*` → MYPAY
2. **Per configurazione ente**: se l'ente ha un record attivo in `mygov_ente_config_pu` → **PIATTAFORMA_UNITARIA** (OAuth2); altrimenti → **LEGACY** (forward diretto al backend)

L'identificazione dell'ente avviene estraendo dal body SOAP il tag `<codIpaEnte>` oppure, in subordine, `<identificativoDominio>` (codice fiscale risolto tramite cache).

---

## Architettura

```
┌─────────┐       SOAP        ┌──────────────────┐      OAuth2 + SOAP      ┌─────────────────────┐
│   SIL   │ ───────────────►  │   Middleware      │ ──────────────────────► │ Piattaforma Unitaria│
│  (Enti) │ ◄───────────────  │  mypay.mypaycore  │ ◄────────────────────── │     (pagoPA)        │
└─────────┘                   └──────┬───────────┘                         └─────────────────────┘
                                     │
                                     │ Forward diretto (enti non migrati)
                                     ▼
                              ┌──────────────┐
                              │ Backend Legacy│
                              │ MyPay/MyPivot │
                              └──────────────┘
```

**Componenti principali**:

| Componente | Classe | Responsabilità |
|---|---|---|
| Proxy SOAP | `AbstractSoapProxyEndpoint` | Classe base per tutti gli endpoint SOAP proxy |
| Autenticazione OAuth2 | `OAuthTokenService` | Gestione token per-ente con cache in memoria |
| Client PU | `PiattaformaUnitariaClient` | Comunicazione HTTP verso la Piattaforma Unitaria |
| Client Legacy | `ProxyForwardingClient` | Forward diretto ai backend MyPay/MyPivot |
| Routing | `RoutingDecisionService` | Decisione di instradamento per ente e path |
| Cache enti | `EnteCacheService` | Cache duale con TTL configurabile |
| Upload proxy | `UploadFlussoController` | Proxy REST per upload file verso PU |
| Transaction log | `TransactionLoggingService` | Audit su DB di ogni transazione |
| Metriche | `MiddlewareMetricsService` | Metriche Micrometer per Actuator |

---

## Struttura del repository

```
mypay.mypaycore/                          ← root, parent POM
├── mypay.mypaycore-springboot/           ← applicazione Spring Boot
│   └── src/
│       ├── main/java/.../api/
│       │   ├── auth/                     ← OAuth2 token management
│       │   ├── client/                   ← client HTTP (PU, legacy, upload)
│       │   ├── config/                   ← configurazioni Spring
│       │   ├── domain/                   ← entità di dominio
│       │   ├── health/                   ← health indicator personalizzati
│       │   ├── logging/                  ← logging strutturato e transaction log
│       │   ├── metrics/                  ← metriche Micrometer
│       │   ├── repository/              ← repository Jdbi (non JPA)
│       │   ├── routing/                  ← logica di instradamento
│       │   ├── soap/endpoint/            ← 10 endpoint SOAP proxy
│       │   ├── upload/                   ← proxy REST per upload flussi
│       │   └── util/                     ← utilità condivise
│       └── main/resources/config/        ← application.properties
├── mypay.mypaycore-properties/           ← properties per deploy (SSL, prod)
├── mypay.mypaycore-db/                   ← script SQL (DDL e DML)
├── mypay.mypaycore-release/              ← packaging per rilascio
└── docs/                                 ← documentazione tecnica
```

---

## Prerequisiti

| Requisito | Versione | Note |
|---|---|---|
| JDK | 17 | Obbligatorio (non compatibile con versioni successive) |
| Maven | 3.8+ | Incluso in IntelliJ oppure standalone |
| PostgreSQL | 12+ | Database condiviso con MyPay/MyPivot |
| IntelliJ IDEA | 2023+ | Consigliato (configurazione documentata sotto) |
| Connettività | — | Accesso alla rete interna ARIA e alla PU UAT |

### Dipendenze principali

| Dipendenza | Versione |
|---|---|
| Spring Boot | 3.5.5 |
| SpringLine2 | 2027.01.01 |
| Resilience4j | 2.2.0 |
| Jdbi 3 | 3.27.0 |
| PostgreSQL Driver | gestita da Spring Boot |
| Jasypt | 3.0.3 |
| Lombok | gestita da Spring Boot |

---

## Configurazione database

Il middleware utilizza il database PostgreSQL già esistente di MyPay (`mypay4.pa`). Il datasource è configurato con il prefisso **`spring.datasource.pa.*`** (non il prefisso standard Spring).

### Tabelle utilizzate

| Tabella | Tipo | Descrizione |
|---|---|---|
| `mygov_ente` | Pre-esistente | Anagrafica enti (codIpa, codiceFiscale, password SIL) |
| `mygov_ente_config_pu` | Creata dal middleware | Credenziali OAuth2 per-ente (client_id, client_secret) |
| `mygov_mw_transaction_log` | Creata dal middleware | Log audit di ogni transazione |

### Script SQL da eseguire

Gli script si trovano in `mypay.mypaycore-db/src/main/sql/` e vanno eseguiti in ordine:

1. `002_CREATE_MWPAY_TRANSACTION_LOG.sql` — crea tabella transaction log
2. `004_CREATE_MYGOV_ENTE_CONFIG_PU.sql` — crea tabella configurazione enti PU

Dopo la creazione delle tabelle, inserire almeno un record in `mygov_ente_config_pu` per ogni ente da instradare verso la Piattaforma Unitaria (consultare `006_INSERT_ENTE_CONFIG_PU_EXAMPLE.sql` come esempio).

---

## Configurazione applicativa

I file di configurazione si trovano in:
- `mypay.mypaycore-springboot/src/main/resources/config/application.properties` — configurazione base
- `mypay.mypaycore-springboot/src/main/resources/config/application-dev.properties` — override per profilo `dev`

### Parametri principali da configurare

#### Datasource (in `application-dev.properties`)

```properties
spring.datasource.pa.url=${DB_PA_URL:jdbc:postgresql://HOSTNAME:5432/mypay4.pa}
spring.datasource.pa.username=${DB_PA_USERNAME:mypay4.pa}
spring.datasource.pa.password=${DB_PA_PASSWORD:mypay4.pa}
spring.datasource.pa.hikari.minimumIdle=1
spring.datasource.pa.hikari.maximumPoolSize=5
```

Sostituire `HOSTNAME` con l'indirizzo del server PostgreSQL oppure configurare le variabili d'ambiente.

#### Piattaforma Unitaria (in `application.properties`)

```properties
piattaforma-unitaria.base-url=https://api.uat.p4pa.pagopa.it
piattaforma-unitaria.auth.token-url=${piattaforma-unitaria.base-url}/pu/auth/oauth/token
piattaforma-unitaria.auth.grant-type=client_credentials
piattaforma-unitaria.auth.scope=openid
```

Le credenziali OAuth2 (client_id, client_secret) sono **per-ente** e configurate in database nella tabella `mygov_ente_config_pu`.

#### Backend legacy

```properties
backend.mypay.base-url=${BACKEND_MYPAY_URL:http://localhost:8080}
backend.mypivot.base-url=${BACKEND_MYPIVOT_URL:http://localhost:8081}
```

#### Porta server (profilo dev)

```properties
server.port=${SERVER_PORT:8086}
```

#### Logging (profilo dev)

```properties
logging.config=classpath:logback-spring.xml
logging.file.dir=C:\\app\\mypay\\middleware\\log
```

Assicurarsi che la directory di log esista oppure modificare il path.

---

## Variabili d'ambiente

Il middleware supporta le seguenti variabili d'ambiente per sovrascrivere i valori di default:

| Variabile | Default | Descrizione |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | Profilo Spring attivo (usare `dev`) |
| `SERVER_PORT` | `8086` | Porta HTTP del server |
| `DB_PA_URL` | `jdbc:postgresql://10.199.144.62:5432/mypay4.pa` | URL JDBC del database |
| `DB_PA_USERNAME` | `mypay4.pa` | Utente database |
| `DB_PA_PASSWORD` | `mypay4.pa` | Password database |
| `BACKEND_MYPAY_URL` | `http://localhost:8080` | URL backend MyPay legacy |
| `BACKEND_MYPIVOT_URL` | `http://localhost:8081` | URL backend MyPivot legacy |
| `MIDDLEWARE_UPLOAD_BASE_URL` | `http://localhost:8086` | URL base per il proxy upload |

---

## Compilazione

### Da riga di comando (Windows)

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn clean install -Pdev -U -Denforcer.skip=true"
```

> **Nota**: il flag `-Denforcer.skip=true` è obbligatorio perché il parent POM corporate richiede OS Unix.

### Solo compilazione (senza test)

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn compile -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

### Esecuzione test

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn test -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

---

## Configurazione IntelliJ IDEA

Per lavorare con il progetto in IntelliJ IDEA sono necessarie **due Run Configuration**:

### 1. Maven Build — `mypay.mypaycore [clean,install,-Pdev,-U]`

Questa configurazione compila e pacchettizza il progetto.

- **Tipo**: Maven
- **Name**: `mypay.mypaycore [clean,install,-Pdev,-U]`
- **Run (command line)**: `clean install -Pdev -U`
- **Working directory**: `mypay.mypaycore` (root del progetto)
- **Profiles**: (vuoto)
- **Maven Options**: Inherit from settings

Per crearla: `Run` → `Edit Configurations` → `+` → `Maven`, impostare i campi come sopra.

### 2. Application — Avvio dell'applicazione

Questa configurazione avvia il middleware in modalità sviluppo.

- **Tipo**: Application
- **Name**: `Application`
- **JDK**: `java 17`
- **Classpath (-cp)**: `mypay.mypaycore-springboot`
- **Main class**: `it.ariaspa.mypay.mypaycore.api.Application`
- **Working directory**: `C:\Users\<UTENTE>\...\middleware svn remoto` (root del progetto)
- **Environment variables**: `SPRING_PROFILES_ACTIVE=dev`
- **Program arguments**: (vuoto)

Per crearla: `Run` → `Edit Configurations` → `+` → `Application`, impostare i campi come sopra.

> **Importante**: la variabile d'ambiente `SPRING_PROFILES_ACTIVE=dev` è obbligatoria. Senza di essa l'applicazione non avvierà il profilo corretto e potrebbe non trovare le configurazioni di datasource e sicurezza.

### Ordine di esecuzione

1. Eseguire prima la **Maven Build** (`Run`) per compilare e pacchettizzare
2. Poi avviare l'**Application** (`Run` o `Debug`) per lanciare il middleware

---

## Esecuzione

Una volta compilato e configurato, il middleware parte sulla porta configurata (default `8086` in profilo `dev`).

### Verifica avvio

```
GET http://localhost:8086/actuator/health
```

Risposta attesa:
```json
{
  "status": "UP",
  "components": {
    "enteConfig": { "status": "UP" },
    "oauthToken": { "status": "UP" },
    "piattaformaUnitaria": { "status": "UP" }
  }
}
```

---

## Endpoint esposti

### Endpoint SOAP (sotto `/ws/`)

| Path | Endpoint | Tipo |
|---|---|---|
| `/ws/pa/PagamentiTelematiciDovutiPagati` | PagamentiTelematiciDovutiPagatiEndpoint | MyPay |
| `/ws/pa/PagamentiTelematiciEsito` | PagamentiTelematiciEsitoEndpoint | MyPay |
| `/ws/pa/PagamentiTelematiciFlussiSPC` | PagamentiTelematiciFlussiSPCEndpoint | MyPay |
| `/ws/pa/PagamentiTelematiciCCPPa` | PagamentiTelematiciCCPPaEndpoint | MyPay |
| `/ws/fesp/PagamentiTelematiciCCP` | PagamentiTelematiciCCPEndpoint | MyPay FESP |
| `/ws/fesp/PagamentiTelematiciCCP25` | PagamentiTelematiciCCP25Endpoint | MyPay FESP |
| `/ws/fesp/PagamentiTelematiciRP` | PagamentiTelematiciRPEndpoint | MyPay FESP |
| `/ws/fesp/PagamentiTelematiciRT` | PagamentiTelematiciRTEndpoint | MyPay FESP |
| `/ws/fesp/PagamentiTelematiciAvvisiDigitali` | PagamentiTelematiciAvvisiDigitaliEndpoint | MyPay FESP |
| `/ws/pivot/Reconciliation` | ReconciliationEndpoint | MyPivot |

### Endpoint REST

| Path | Metodo | Descrizione |
|---|---|---|
| `/api/upload/**` | POST | Proxy upload flussi verso PU (max 100 MB) |

### Endpoint Actuator

| Path | Descrizione |
|---|---|
| `/actuator/health` | Stato di salute (include PU, OAuth, ente config) |
| `/actuator/info` | Informazioni applicazione |
| `/actuator/metrics` | Metriche Micrometer |
| `/actuator/circuitbreakers` | Stato circuit breaker |
| `/actuator/retries` | Stato retry |

---

## Resilienza

Il middleware implementa pattern di resilienza tramite **Resilience4j** su due target:

### Circuit Breaker

| Parametro | Piattaforma Unitaria | Backend Legacy |
|---|---|---|
| Tipo finestra | COUNT_BASED | COUNT_BASED |
| Dimensione finestra | 10 (dev: 5) | 10 (dev: 5) |
| Soglia fallimenti | 50% (dev: 80%) | 50% (dev: 80%) |
| Durata stato OPEN | 30s (dev: 10s) | 30s (dev: 10s) |
| Chiamate in HALF_OPEN | 3 | 3 |

### Retry con backoff esponenziale

| Parametro | Piattaforma Unitaria | Backend Legacy |
|---|---|---|
| Tentativi massimi | 3 (dev: 2) | 3 (dev: 2) |
| Attesa iniziale | 1s (dev: 500ms) | 1s (dev: 500ms) |
| Backoff esponenziale | Abilitato (x2) | Abilitato (x2) |

Eccezioni gestite: `PiattaformaCommunicationException`, `ResourceAccessException`.
Eccezioni ignorate (no retry): `PiattaformaAuthenticationException`.

---

## Monitoraggio e Health Check

Tre health indicator personalizzati:

| Indicatore | Classe | Verifica |
|---|---|---|
| Piattaforma Unitaria | `PiattaformaUnitariaHealthIndicator` | Connettività verso la PU |
| OAuth Token | `OAuthTokenHealthIndicator` | Stato dei token OAuth2 in cache |
| Ente Config | `EnteConfigHealthIndicator` | Disponibilità configurazioni enti |

---

## Profili applicativi

| Profilo | Scopo | Stato |
|---|---|---|
| `dev` | Sviluppo locale — sicurezza rilassata, logging DEBUG, resilienza rilassata | **Attivo** |
| `uat` | Test di integrazione | Da creare |
| `prod` | Produzione — SSL, sicurezza JWT, logging WARN | Da creare |

---

## Struttura database

### `mygov_ente` (pre-esistente)

| Colonna | Tipo | Descrizione |
|---|---|---|
| `mygov_ente_id` | BIGINT PK | ID ente |
| `cod_ipa_ente` | VARCHAR | Codice IPA (chiave logica) |
| `de_nome_ente` | VARCHAR | Nome descrittivo |
| `codice_fiscale_ente` | VARCHAR | Codice fiscale |
| `cd_stato_ente` | VARCHAR | Stato (ATTIVO/DISATTIVO) |
| `de_password` | VARCHAR | Password SIL per autenticazione SOAP |

### `mygov_ente_config_pu`

| Colonna | Tipo | Descrizione |
|---|---|---|
| `id` | BIGINT PK | ID configurazione |
| `codice_ipa_ente` | VARCHAR FK | Riferimento a `mygov_ente.cod_ipa_ente` |
| `client_id` | VARCHAR | Client ID OAuth2 per la PU |
| `client_secret` | VARCHAR | Client Secret OAuth2 per la PU |
| `attivo` | BOOLEAN | Abilita routing verso PU |
| `dt_creazione` | TIMESTAMP | Data creazione record |
| `dt_ultima_modifica` | TIMESTAMP | Data ultima modifica |

### `mygov_mw_transaction_log`

| Colonna | Tipo | Descrizione |
|---|---|---|
| `id` | BIGINT PK | ID transazione |
| `cod_ipa_ente` | VARCHAR | Codice IPA dell'ente |
| `tipo_operazione` | VARCHAR | Nome operazione SOAP |
| `modalita_routing` | VARCHAR | PIATTAFORMA_UNITARIA / LEGACY / SCONOSCIUTA |
| `destinazione` | VARCHAR | MYPAY / MYPIVOT / SCONOSCIUTA |
| `path_richiesta` | VARCHAR | Path della richiesta HTTP |
| `http_status_risposta` | INTEGER | Codice HTTP della risposta |
| `esito` | VARCHAR | OK / ERRORE |
| `messaggio_errore` | TEXT | Dettaglio errore (se presente) |
| `durata_ms` | BIGINT | Durata in millisecondi |
| `timestamp_richiesta` | TIMESTAMP | Timestamp della richiesta |

---

## Repository SVN

```
http://cm-lispa-scm.adlispa.local/repo/sw/
```

Struttura organizzata secondo lo standard Subversion trunk/tags/branches.
