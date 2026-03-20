# Documento di Architettura - MyPay Middleware (mypay.mypaycore)

**Versione:** 1.2  
**Data:** 20 Marzo 2026  
**Stato:** Fondamenta completate + logging custom, persistenza plumbing, resilienza e test unitari  

---

## 1. Introduzione

### 1.1 Scopo del Documento

Questo documento descrive l'architettura del middleware **mypay.mypaycore**, un sistema di integrazione sviluppato per ARIA S.p.A. / Regione Lombardia. Il documento copre:

- La posizione del middleware nell'ecosistema MyPay
- L'architettura tecnica implementata nella Fase 1
- La struttura del codice e dei moduli
- Le scelte progettuali e le motivazioni
- Le evoluzioni previste per le fasi successive

### 1.2 Scopo del Progetto

Il middleware **mypay.mypaycore** funge da layer di comunicazione e orchestrazione tra i **Sistemi Informativi Locali (SIL)** degli enti e la **Piattaforma Unitaria** collegata a **pagoPA**.

Il sistema permette ai SIL di interagire con i servizi della piattaforma di pagamento attraverso un unico punto di accesso centralizzato, gestendo in modo trasparente:

- L'autenticazione OAuth2 verso la piattaforma
- L'inoltro delle richieste SOAP
- La restituzione delle risposte

---

## 2. Contesto di Sistema

### 2.1 Posizione nell'Ecosistema

Il middleware si colloca al centro dell'infrastruttura MyPay, come mostrato nel diagramma di riferimento (`docs/IMMAGINE_STRUTTURA_MIDDLEWARE.png`).

I sistemi con cui il middleware interagisce direttamente sono:

| Sistema | Direzione | Protocollo | Descrizione |
|---------|-----------|------------|-------------|
| **SIL** (Sistemi Informativi Locali) | Ingresso | SOAP/XML | Sistemi applicativi degli enti che inviano richieste di pagamento e riconciliazione |
| **Piattaforma Unitaria** | Uscita | SOAP/XML + OAuth2 | Piattaforma collegata a pagoPA per la gestione dei pagamenti |
| **Database MyPay Middleware** | Interno | JDBC (Oracle) | Base dati del middleware (prevista per fasi successive) |
| **MALL** | Collegamento | - | Sistema di monitoraggio e logging centralizzato |
| **ARPAC** | Ingresso | - | Sistema per i giornali di cassa |
| **SAP** | Ingresso | - | Sistema per le reversali di pagamento |

### 2.2 Attori del Sistema

**SIL (Sistemi Informativi Locali)**
- Sistemi applicativi degli enti sanitari e regionali
- Inviano richieste SOAP al middleware
- Ricevono le risposte elaborate dal middleware
- Si autenticano tramite `codIpaEnte` (nell'Header SOAP) + `password` (nel Body SOAP)
- NON inviano JWT/Bearer token — l'autenticazione OAuth2 è gestita internamente dal middleware

**Middleware (questo progetto)**
- Punto di accesso unico per i SIL
- Gestisce l'autenticazione OAuth2 verso la piattaforma
- Inoltra le richieste e restituisce le risposte
- Implementa caching del token e gestione degli errori

**Piattaforma Unitaria (pagoPA)**
- Sistema esterno che espone servizi per la gestione dei pagamenti
- Richiede autenticazione OAuth2 (Client Credentials Flow)
- Accetta richieste SOAP autenticate con Bearer token JWT

---

## 3. Architettura di Alto Livello

### 3.1 Flusso Principale

```
SIL
 │
 │  SOAP Request (Envelope completo con Header + Body)
 │  Content-Type: text/xml
 ▼
┌─────────────────────────────────────────────────────┐
│              MYPAY MIDDLEWARE                        │
│                                                     │
│  ┌─────────────────┐    ┌──────────────────────┐   │
│  │ SOAP Endpoint    │───>│ PiattaformaUnitaria  │   │
│  │ (Spring WS)      │    │ Client               │   │
│  │ ReconciliationEP │<───│ (RestTemplate)        │   │
│  └─────────────────┘    └──────────┬───────────┘   │
│                                     │               │
│                          ┌──────────▼───────────┐   │
│                          │ OAuth2 Token Service  │   │
│                          │ (Cache in-memory)     │   │
│                          └──────────┬───────────┘   │
│                                     │               │
└─────────────────────────────────────┼───────────────┘
                                      │
                                      │  POST /pu/auth/oauth/token
                                      │  (client_credentials)
                                      ▼
                            Piattaforma Unitaria
                                      │
                                      ▼
                                   pagoPA
```

### 3.2 Flusso Dettagliato di una Richiesta

1. Il **SIL** invia una richiesta SOAP al middleware (con `codIpaEnte` nell'Header e `password` nel Body)
2. Il middleware riceve la richiesta tramite Spring WS ed estrae l'Envelope SOAP completo
3. L'endpoint SOAP (`ReconciliationEndpoint`) usa il `MessageContext` per ottenere Header + Body
4. Il `PiattaformaUnitariaClient` prepara la richiesta verso la piattaforma
5. L'`OAuthTokenInterceptor` aggiunge automaticamente il token OAuth2 Bearer
6. Se il token non esiste o e scaduto, l'`OAuthTokenService` ne richiede uno nuovo
7. L'Envelope SOAP completo viene inviato alla Piattaforma Unitaria con il Bearer token
8. Se la piattaforma risponde 401, il client effettua un refresh del token e un retry automatico della richiesta
9. Il body viene estratto dalla risposta PU e restituito al SIL in un nuovo Envelope SOAP

### 3.3 Flusso di Autenticazione OAuth2

```
OAuthTokenService                    Piattaforma Unitaria
     │                                       │
     │  POST /pu/auth/oauth/token            │
     │  ?client_id=SELC_...                  │
     │  &client_secret=xxxxx                 │
     │  &grant_type=client_credentials       │
     │  &scope=openid                        │
     │  (parametri come query string)        │
     │  ──────────────────────────────────>  │
     │                                       │
     │  <────────────────────────────────────│
     │  {                                    │
     │    "access_token": "eyJhbGci...",     │
     │    "token_type": "Bearer",            │
     │    "expires_in": 3600                 │
     │  }                                    │
     │                                       │
     ▼                                       │
  Cache in-memory                            │
  (con margine di 60s)                       │
```

---

## 4. Stack Tecnologico

### 4.1 Framework e Librerie Principali

| Componente | Tecnologia | Versione |
|-----------|------------|---------|
| **Framework base** | Spring Boot | 3.5.5 |
| **Framework aziendale** | SpringLine2 | 2027.01.01 |
| **Java** | Oracle JDK | 17 LTS |
| **Build** | Apache Maven | 3.9.9 |
| **SOAP Server** | Spring Web Services | (gestito da spring-boot-starter-web-services) |
| **SOAP Client** | SpringLine2 WS | 2027.01.01 |
| **XML Binding** | Jakarta XML Bind (JAXB) | (gestito da BOM) |
| **Logging** | SLF4J + Logback + marker custom + SpringLine2 MON/MON-APP | Integrato |
| **Sicurezza** | SpringLine2 Security (JWT/Propagator) | Integrato |
| **API Docs** | SpringLine2 OpenAPI (Swagger) | Integrato |

### 4.2 Moduli SpringLine2 Utilizzati

| Modulo | Artifact ID | Utilizzo nel Middleware |
|--------|------------|----------------------|
| **Core** | `springline2-core` | Context, Logging MON/MON-APP, Security, API Rest |
| **OpenAPI** | `springline2-openapi` | Documentazione Swagger-UI |
| **WS** | `springline2-ws` | Client SOAP con supporto alla propagazione |

**Moduli disabilitati (Fase 1):**
- `springline2-data` - non utilizzato; la persistenza attuale e' gestita con configurazione manuale DataSource + Jdbi
- `springline2-jms` - ActiveMQ (non necessario in Fase 1)

---

## 5. Struttura del Progetto

### 5.1 Struttura Multi-Modulo Maven

```
mypay.mypaycore/                              (Parent POM - packaging: pom)
│
├── pom.xml                                   (Parent: versioni, moduli, proprieta)
│
├── mypay.mypaycore-springboot/               (Modulo applicativo principale)
│   ├── pom.xml                               (Dipendenze applicative)
│   └── src/
│       ├── main/java/                        (Codice sorgente)
│       └── main/resources/config/            (Configurazione applicativa)
│
├── mypay.mypaycore-properties/               (Configurazione per il deploy)
│   ├── pom.xml                               (Packaging: zip)
│   └── src/main/resources/                   (properties, bootstrap, logback, script shell)
│
├── mypay.mypaycore-db/                       (Script database)
│   ├── pom.xml                               (Packaging: zip)
│   └── src/main/sql/                         (Script SQL)
│
├── mypay.mypaycore-release/                  (Aggregatore per il rilascio)
│   └── pom.xml                               (Assembla jar + properties + db)
│
└── docs/                                     (Documentazione)
    ├── architettura/                         (Questo documento)
    ├── springline2/                          (Guide SpringLine2)
    └── tasks/                                (Task di sviluppo e piani)
```

### 5.2 Struttura dei Pacchetti Java

```
it.ariaspa.mypay.mypaycore.api/
│
├── Application.java                          Punto di ingresso Spring Boot
│
├── config/
│   ├── PiattaformaUnitariaConfig.java       Configurazione URL e credenziali OAuth2
│   └── SoapWebServiceConfig.java            Registrazione MessageDispatcherServlet
│
├── logging/
│   ├── JdbiSqlLogger.java                   Logger SQL custom per Jdbi
│   └── LogMarker.java                       Marker SLF4J centralizzati
│
├── auth/
│   ├── dto/
│   │   └── OAuthTokenResponse.java          DTO risposta token OAuth2
│   ├── OAuthTokenService.java               Login OAuth2 + cache in-memory del token
│   └── OAuthTokenInterceptor.java           Interceptor Bearer token per richieste uscenti
│
├── soap/
│   └── endpoint/
│       └── ReconciliationEndpoint.java      Endpoint SOAP per riconciliazione
│
├── client/
│   └── PiattaformaUnitariaClient.java       Client HTTP verso Piattaforma Unitaria
│
├── util/
│   ├── Constants.java                       Contenitore centralizzato delle costanti
│   ├── LogHelper.java                       Utility per firme metodo leggibili nei log
│   └── Utilities.java                       Helper tecnici riusabili
│
└── common/
    └── exception/
        └── PiattaformaAuthenticationException.java   Eccezione autenticazione OAuth2
```

---

## 6. Componenti Implementati (Fase 1)

### 6.1 Modulo Configurazione

**`PiattaformaUnitariaConfig`** (`config/PiattaformaUnitariaConfig.java`)

Classe `@Configuration` + `@ConfigurationProperties(prefix = "piattaforma-unitaria")` che mappa automaticamente le proprieta in formato `.properties` in un oggetto Java. Contiene:

- `baseUrl` - URL base della Piattaforma Unitaria
- `auth.tokenUrl` - Endpoint OAuth2 per il login
- `auth.clientId` - Identificativo del client
- `auth.clientSecret` - Segreto del client (esternalizzabile via variabile d'ambiente)
- `auth.grantType` - Tipo di grant (default: `client_credentials`)
- `auth.scope` - Scope OAuth2 (default: `openid`)

Le credenziali sono configurate per supportare variabili d'ambiente:
```properties
piattaforma-unitaria.auth.client-id=${PIATTAFORMA_CLIENT_ID:SELC_99999000013SIL_RegLomb2}
piattaforma-unitaria.auth.client-secret=${PIATTAFORMA_CLIENT_SECRET:xxxxx}
```

**`SoapWebServiceConfig`** (`config/SoapWebServiceConfig.java`)

Classe `@EnableWs` + `@Configuration` che implementa `WsConfigurer`. Registra un `MessageDispatcherServlet` mappato su `/pu/sil/soap/*` per gestire tutte le richieste SOAP in ingresso dai SIL.

### 6.2 Modulo Autenticazione

**`OAuthTokenResponse`** (`auth/dto/OAuthTokenResponse.java`)

DTO che rappresenta la risposta dell'endpoint OAuth2. Utilizza `@JsonProperty` per il mapping dei campi JSON (`access_token`, `token_type`, `expires_in`). Il metodo `toString()` nasconde il token per sicurezza (`[REDACTED]`).

**`OAuthTokenService`** (`auth/OAuthTokenService.java`)

Servizio centrale per la gestione del ciclo di vita del token OAuth2:

- **Richiesta token**: POST verso l'endpoint OAuth2 con parametri `client_credentials`
- **Cache in-memory**: Il token viene salvato in una variabile `volatile` con la sua scadenza
- **Verifica scadenza**: Controllo con margine di sicurezza di 60 secondi per evitare utilizzo di token in procinto di scadere
- **Thread-safety**: Utilizzo di `ReentrantLock` con pattern double-check locking per garantire che un solo thread alla volta richieda un nuovo token
- **Refresh forzato**: Metodo `refreshToken()` per invalidare il token corrente e richiederne uno nuovo (utilizzato dopo errore 401)

**`OAuthTokenInterceptor`** (`auth/OAuthTokenInterceptor.java`)

Implementazione di `ClientHttpRequestInterceptor` che intercetta ogni richiesta HTTP in uscita verso la piattaforma e aggiunge automaticamente l'header `Authorization: Bearer <token>`. Il token viene ottenuto dal `OAuthTokenService`.

### 6.3 Modulo Endpoint SOAP

**`ReconciliationEndpoint`** (`soap/endpoint/ReconciliationEndpoint.java`)

Endpoint Spring WS annotato con `@Endpoint` che utilizza l'approccio **contract-last**:

- `@PayloadRoot(namespace, localPart)` per il routing delle richieste SOAP
- Namespace: `http://www.regione.veneto.it/pagamenti/pivot/ente/`
- Operazione: `pivotSILAutorizzaImportFlussoTesoreria`
- Approccio **proxy trasparente**: inietta il `MessageContext` per accedere all'Envelope SOAP completo (Header + Body)
- Estrae l'Envelope completo tramite `SoapMessage.writeTo()` e lo inoltra integralmente al `PiattaformaUnitariaClient`
- Dalla risposta della PU, estrae il contenuto del Body SOAP e lo restituisce come `Element` DOM per il re-wrapping da parte di Spring WS

### 6.4 Modulo Client Piattaforma

**`PiattaformaUnitariaClient`** (`client/PiattaformaUnitariaClient.java`)

Client HTTP configurato con l'`OAuthTokenInterceptor` per l'autenticazione automatica:

- `RestTemplate` inizializzato con l'interceptor in `@PostConstruct`
- Metodo `forwardSoapRequest(path, soapXml)` per l'inoltro delle richieste
- Gestione intelligente dell'errore 401: in caso di risposta Unauthorized, il client effettua un refresh del token e un retry automatico della richiesta
- Content-Type: `text/xml` per le richieste SOAP

### 6.5 Gestione Eccezioni

**`PiattaformaAuthenticationException`** (`common/exception/PiattaformaAuthenticationException.java`)

Eccezione runtime dedicata ai fallimenti di autenticazione OAuth2, con supporto per cause annidate.

### 6.6 Sicurezza

La catena di sicurezza SpringLine2 e configurata nel file base `application.properties` con JWT abilitato sugli endpoint SOAP. Tuttavia, nel profilo **dev** (`application-dev.properties`), la sicurezza JWT e **disabilitata** perche i SIL non inviano token JWT — si autenticano tramite `codIpaEnte` + `password` nel messaggio SOAP:

**Configurazione base (`application.properties`):**

| Path Pattern | Tipo di Autenticazione | Descrizione |
|-------------|----------------------|-------------|
| `/favicon.ico`, `/swagger-ui/**`, `/v3/api-docs/**` | Anonymous | Accesso pubblico |
| `/pu/sil/soap/**` | JWT + Propagator | Configurazione predefinita (per ambienti con gateway) |

**Configurazione dev (`application-dev.properties`) — override:**

| Path Pattern | Tipo di Autenticazione | Descrizione |
|-------------|----------------------|-------------|
| `/**` | Anonymous | JWT disabilitato, accesso libero per test diretti |

> **Nota:** In ambiente di produzione, la sicurezza JWT potrebbe essere gestita da un API Gateway a monte del middleware. Il profilo dev disabilita questa protezione per permettere test end-to-end diretti dai SIL o da Postman.

### 6.7 Logging

Il modello di logging del middleware combina il supporto di SpringLine2 con una configurazione Logback custom e marker SLF4J centralizzati.

**Componenti principali**:

- `LogMarker` centralizza i marker semantici del progetto (`MONITORING`, `REST`, `SOAP_SERVER`, `SOAP_CLIENT`, `METHOD`, `DB_STATEMENT`, `DB_CONNECTION_POOL`)
- `LogHelper` converte oggetti `Method` in firme leggibili per i log tecnici e SQL
- `JdbiSqlLogger` traccia query, tempo di esecuzione, binding, stato read-only e query lente del layer Jdbi
- `logback-spring.xml` nel modulo `mypay.mypaycore-properties` separa i flussi di log su file dedicati

**File di log previsti**:

| File | Contenuto |
|------|-----------|
| `backend.log` | Log tecnico generale del backend |
| `mon.log` | Monitoraggio sintetico |
| `app.log` | Log applicativi dedicati |
| `audit.log` | Eventi di audit |
| `filter.log` | Request tracing, filtri e tracing SOAP/client |

**Integrazione SpringLine2**:

- Richieste HTTP in ingresso (`spl.http.logging.mon.enabled=true`)
- Richieste HTTP in ingresso - livello applicativo (`spl.http.logging.app.enabled=true`)
- Chiamate REST client in uscita (`spl.client-rest.logging.mon.enabled=true`)
- Chiamate REST client in uscita - livello applicativo (`spl.client-rest.logging.app.enabled=true`)

Questo approccio permette di distinguere meglio i log infrastrutturali, applicativi, di monitoraggio e di audit senza disperdere stringhe o convenzioni direttamente nel codice.

---

## 7. Configurazione

### 7.1 Configurazione Applicativa (application.properties)

La configurazione principale e' organizzata in tre sezioni:

**Configurazione DataSource/Jdbi** (profilo `dev`):
```properties
spring.datasource.pa.driver-class-name=org.postgresql.Driver
spring.datasource.pa.url=${DB_PA_URL:jdbc:postgresql://localhost:5432/mypay_local_copy}
spring.datasource.pa.username=${DB_PA_USERNAME:admin}
spring.datasource.pa.password=${DB_PA_PASSWORD:admin}
spring.datasource.pa.hikari.minimum-idle=1
spring.datasource.pa.hikari.maximum-pool-size=5
```

**Piattaforma Unitaria** (ambiente UAT):
```properties
piattaforma-unitaria.base-url=https://api.uat.p4pa.pagopa.it
piattaforma-unitaria.auth.token-url=${piattaforma-unitaria.base-url}/pu/auth/oauth/token
piattaforma-unitaria.auth.client-id=${PIATTAFORMA_CLIENT_ID:SELC_99999000013SIL_RegLomb2}
piattaforma-unitaria.auth.client-secret=${PIATTAFORMA_CLIENT_SECRET:xxxxx}
piattaforma-unitaria.auth.grant-type=client_credentials
piattaforma-unitaria.auth.scope=openid
```

**SpringLine2** (sicurezza, logging):
```properties
spl.security.authentication.anonymous.uri-matchers=/favicon.ico,/swagger-ui/**,/v3/api-docs/**
spl.security.authentication.jwt.uri-matchers=/pu/sil/soap/**
spl.security.authentication.propagator.uri-matchers=/pu/sil/soap/**
spl.http.logging.mon.enabled=true
spl.http.logging.app.enabled=true
spl.client-rest.logging.mon.enabled=true
spl.client-rest.logging.app.enabled=true
```

**Logback applicativo** (`mypay.mypaycore-properties/src/main/resources/logback-spring.xml`):
```properties
logging.file.dir=<directory-log>
```

La directory definita da `logging.file.dir` viene usata come radice per i file `backend.log`, `mon.log`, `app.log`, `audit.log` e `filter.log`.

### 7.2 Configurazione Sviluppo Locale

Per lo sviluppo locale il progetto usa il profilo `dev` del modulo `mypay.mypaycore-springboot` e il modulo `mypay.mypaycore-properties` come template di deploy.

I file rilevanti sono:

- `mypay.mypaycore-springboot/src/main/resources/config/application.properties`
- `mypay.mypaycore-springboot/src/main/resources/config/application-dev.properties`
- `mypay.mypaycore-springboot/src/main/resources/config/bootstrap.properties`
- `mypay.mypaycore-properties/src/main/resources/application.properties`
- `mypay.mypaycore-properties/src/main/resources/logback-spring.xml`

### 7.3 Variabili d'Ambiente

| Variabile | Descrizione | Valore Default |
|-----------|-------------|----------------|
| `PIATTAFORMA_CLIENT_ID` | Client ID OAuth2 | `SELC_99999000013SIL_RegLomb2` |
| `PIATTAFORMA_CLIENT_SECRET` | Client Secret OAuth2 | `xxxxx` |

---

## 8. Dipendenze Maven

### 8.1 Dipendenze Attive

| Dipendenza | Scopo |
|-----------|-------|
| `springline2-core` | Framework base: Context, Logging, Security |
| `springline2-openapi` | Documentazione API Swagger/OpenAPI |
| `springline2-ws` | Client SOAP per chiamate alla Piattaforma Unitaria |
| `spring-boot` | Framework Spring Boot |
| `spring-boot-starter-web-services` | Server SOAP (MessageDispatcherServlet, @Endpoint) |
| `jakarta.xml.bind-api` | API JAXB per marshalling/unmarshalling XML |
| `jaxb-runtime` | Implementazione runtime JAXB |

### 8.2 Dipendenze Disabilitate (Fase 1)

| Dipendenza | Motivo | Fase di Attivazione |
|-----------|--------|-------------------|
| `springline2-data` | Nessuna persistenza DB in Fase 1 | Fase 2+ |
| `springline2-jms` | Nessuna messaggistica asincrona in Fase 1 | Fase 3+ |
| `ojdbc11` | Driver Oracle (necessario con springline2-data) | Fase 2+ |
| `HikariCP` | Connection pool (necessario con springline2-data) | Fase 2+ |

---

## 9. Scelte Progettuali e Motivazioni

### 9.1 Approccio Contract-Last per SOAP

Si e scelto un approccio **contract-last** (definizione dell'endpoint in Java, senza WSDL predefinito) per la Fase 1 perche:

- Permette un'implementazione rapida delle fondamenta
- Non richiede la definizione completa dello schema XSD
- Facilita l'iterazione durante lo sviluppo iniziale
- L'evoluzione a contract-first (con WSDL/XSD) e prevista per le fasi successive

### 9.2 Cache Token In-Memory

Si e scelto il caching in-memory del token OAuth2 (anziche su database) perche:

- Riduce la complessita nella Fase 1 (nessuna dipendenza da DB)
- Il token ha una durata limitata (tipicamente 1 ora) e non necessita di persistenza
- Il pattern double-check locking con `ReentrantLock` garantisce thread-safety
- Il margine di 60 secondi evita l'uso di token prossimi alla scadenza

### 9.3 Retry Automatico su 401

Il `PiattaformaUnitariaClient` implementa un meccanismo di retry automatico:

1. Se la piattaforma risponde con 401 Unauthorized
2. Il client forza il refresh del token
3. Ripete la richiesta con il nuovo token
4. Se il retry fallisce, l'errore viene propagato al SIL

Questo approccio gestisce i casi di token invalidato lato server prima della scadenza naturale.

### 9.4 Separazione Moduli Maven

La struttura multi-modulo segue lo standard ARIA:

- **springboot**: Codice applicativo (jar eseguibile)
- **properties**: Configurazione per il deploy (zip)
- **db**: Script database (zip)
- **release**: Aggregatore per il rilascio
- **docs**: Documentazione tecnica, architetturale e operativa del progetto

---

## 10. Evoluzione Prevista

### Fase 2: Persistenza e Modello Dati

- Riattivazione di `springline2-data`, `ojdbc11`, `HikariCP`
- Creazione del Database MyPay Middleware (tabelle per token, log transazioni, configurazione SIL)
- Migrazione della cache token da in-memory a database
- Modello dati per le entita di dominio (pagamenti, riconciliazione, flussi)

### Fase 3: Logica di Business

- Implementazione della logica di riconciliazione
- Gestione dei flussi di tesoreria
- Validazione avanzata delle richieste SOAP
- Mapping e trasformazione dei messaggi tra formato SIL e formato piattaforma

### Fase 4: Endpoint Aggiuntivi

- Nuovi endpoint SOAP per le diverse operazioni (giornali di cassa, reversali, etc.)
- Evoluzione a contract-first con WSDL/XSD completi
- Integrazione con altri sistemi dell'ecosistema (ARPAC, SAP, MALL)

### Fase 5: Resilienza e Operabilita

- Circuit breaker per le chiamate esterne
- Gestione avanzata dei retry con backoff esponenziale
- Health check e metriche
- Configurazione multi-ambiente (dev, test, UAT, produzione)
- Test automatizzati (unitari, integrazione, contract)

### Fase 6: Messaggistica Asincrona

- Attivazione di `springline2-jms` (ActiveMQ)
- Gestione di operazioni asincrone e code di messaggi
- Notifiche e callback

---

## 11. Riferimenti

| Documento | Posizione | Descrizione |
|-----------|-----------|-------------|
| Contesto iniziale | `docs/tasks/1-CONTEXTO_INIZIALE_DI_CREAZIONE_PROGETTO.md` | Requisiti e specifiche della Fase 1 |
| Piano di implementazione | `docs/tasks/Plan.md` | Piano dettagliato dell'implementazione |
| Guida SpringLine2 | `docs/springline2/RIASUNTO_SPRINGLINE2.md` | Riassunto delle funzionalita del framework |
| Linee guida SpringLine2 | `docs/springline2/ARIA-W8B1-LGD@91 v.1.18 - Linee guida...` | Documentazione ufficiale ARIA |
| Diagramma struttura | `docs/IMMAGINE_STRUTTURA_MIDDLEWARE.png` | Diagramma dell'ecosistema MyPay |
