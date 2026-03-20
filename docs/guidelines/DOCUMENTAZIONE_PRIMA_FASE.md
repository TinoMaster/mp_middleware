# DOCUMENTAZIONE_PRIMA_FASE
## Middleware MyPay — Guida Tecnica Completa

**Versione**: 1.5.1  
**Data**: 20 Marzo 2026  
**Stato**: Prima fase completata — Fondazioni operative + Test end-to-end con PU UAT reale

---

## Indice

1. [Cos'è questo progetto](#1-cosè-questo-progetto)
2. [Architettura generale](#2-architettura-generale)
3. [Struttura del progetto Maven](#3-struttura-del-progetto-maven)
4. [Struttura dei pacchetti Java](#4-struttura-dei-pacchetti-java)
5. [Modulo di Configurazione](#5-modulo-di-configurazione)
6. [Modulo di Autenticazione](#6-modulo-di-autenticazione)
7. [Modulo Client Piattaforma](#7-modulo-client-piattaforma)
8. [Modulo Endpoint SOAP](#8-modulo-endpoint-soap)
9. [Gestione degli Errori](#9-gestione-degli-errori)
10. [Monitoraggio e Health Check](#10-monitoraggio-e-health-check)
11. [Resilienza](#11-resilienza)
12. [Ambienti e Profili](#12-ambienti-e-profili)
13. [Test Unitari](#13-test-unitari)
14. [Come avviare il progetto](#14-come-avviare-il-progetto)
15. [Come testare il flusso completo](#15-come-testare-il-flusso-completo)
16. [Cosa NON è ancora implementato](#16-cosa-non-è-ancora-implementato)
17. [Prossimi passi — Fasi Future](#17-prossimi-passi--fasi-future)
18. [Agenti OpenCode](#18-agenti-opencode)

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
       │  POST /pu/sil/soap/reconciliation/...
       │  Content-Type: text/xml
       │  Body: <soapenv:Envelope>
       │          <Header><codIpaEnte>...</codIpaEnte></Header>
       │          <Body><password>...</password></Body>
       │        </soapenv:Envelope>
       ▼
┌─────────────────────────────────────────┐
│         MIDDLEWARE (questo progetto)    │
│                                         │
│  1. Riceve la richiesta SOAP            │
│  2. Estrae l'Envelope completo          │
│     (Header + Body)                     │
│  3. Ottiene token OAuth2 da pagoPA      │
│     (o lo usa dalla cache)              │
│  4. Inoltra l'Envelope completo         │
│     con Bearer token OAuth2             │
│  5. Riceve la risposta                  │
│  6. Estrae il body dalla risposta       │
│  7. Restituisce la risposta al SIL      │
└─────────────────────────────────────────┘
       │
       │  POST /pu/sil/soap/...
       │  Header: Authorization: Bearer <token-OAuth2>
       │  Body: Envelope SOAP completo (stesso del SIL)
       ▼
Piattaforma Unitaria (pagoPA)
       │
       ▼
     pagoPA
```

> **NOTA IMPORTANTE**: Il SIL **NON** invia un JWT/Bearer token. L'autenticazione del SIL
> avviene tramite `codIpaEnte` (nell'Header SOAP) e `password` (nel Body SOAP).
> Il middleware gestisce internamente l'autenticazione OAuth2 verso la Piattaforma Unitaria.

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
├── mypay.mypaycore-db/             ← Script SQL Oracle (placeholder per fase 2)
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
    │   │   ├── health/
    │   │   ├── logging/
    │   │   ├── soap/
    │   │   └── util/
    │   └── resources/config/
     │       ├── application.properties        ← configurazione base (comune a tutti i profili)
     │       ├── application-dev.properties    ← profilo sviluppo (unico profilo attivo)
     │       └── bootstrap.properties
     └── test/
         ├── java/.../api/
         │   ├── auth/OAuthTokenServiceTest.java
         │   ├── client/PiattaformaUnitariaClientTest.java
         │   └── soap/endpoint/ReconciliationEndpointTest.java
         └── resources/config/application.properties
```

### `mypay.mypaycore-db` (modulo database)

Contiene gli script SQL per il database. Attualmente contiene solo `000_PLACEHOLDER.sql`. Il modulo è gestito dal plugin `custom-package-plugin` (ARIA) che produce un archivio ZIP con gli script per il deployment. Gli script reali verranno aggiunti quando si definirà lo schema del database PostgreSQL.

> **Nota**: Il tag `<summary>` è stato rimosso dalla configurazione del plugin perché non riconosciuto dalla versione `3.2.0` (causava un falso positivo in IntelliJ pur non compromettendo la build).

### `mypay.mypaycore-properties` (modulo di configurazione per il deployment)

Contiene i file di configurazione utilizzati durante il deployment su server. I file sono in formato **`.properties`**:

```
mypay.mypaycore-properties/
└── src/main/resources/
    ├── application.properties   ← template di configurazione per il deployment (DataSource, SSL, SpringLine2)
    ├── bootstrap.properties     ← versione applicazione e configurazioni di bootstrap
    ├── logback-spring.xml       ← configurazione Logback per console e file di log
    └── startup.sh               ← script di avvio con --spring.profiles.active=dev
```

Il file `application.properties` in questo modulo è il **template di deployment**: contiene placeholder (`<INSERIRE ...>`) per tutte le proprietà sensibili (credenziali DB, password SSL, segreto JWT). Va completato prima del deploy su ogni ambiente.

Il file `logback-spring.xml` definisce invece la strategia di scrittura dei log su console e su file, separando i flussi tecnici generali da quelli di monitoraggio, audit e tracing.

### `logback-spring.xml`

Il file `mypay.mypaycore-properties/src/main/resources/logback-spring.xml` configura gli appender Logback del progetto e usa la proprieta' `logging.file.dir` come directory base per i file prodotti.

**File di log configurati**:

| File | Contenuto previsto |
|------|--------------------|
| `backend.log` | Log tecnici generali applicativi e di infrastruttura |
| `mon.log` | Log di monitoraggio in formato compatto |
| `app.log` | Log applicativi dedicati in formato compatto |
| `audit.log` | Eventi di audit in formato solo messaggio |
| `filter.log` | Trace di filtri web e message tracing SOAP/client |

**Dettagli operativi**:
- usa `RollingFileAppender` con rotazione giornaliera per tutti i file principali
- mantiene separati i flussi per semplificare troubleshooting, audit e monitoraggio operativo
- lascia il root logger attivo su console e su `backend.log`
- consente di instradare logger specifici su file dedicati in base al package o al ruolo funzionale

---

## 4. Struttura dei pacchetti Java

Tutti i sorgenti si trovano sotto:
`it.ariaspa.mypay.mypaycore.api`

```
api/
├── Application.java                         ← Entry point Spring Boot
│
├── config/                                  ← Configurazione applicazione
│   ├── PiattaformaUnitariaConfig.java
│   ├── SoapWebServiceConfig.java
│   ├── DataSourceConfiguration.java
│   └── JdbiConfiguration.java
│
├── auth/                                    ← Autenticazione OAuth2
│   ├── OAuthTokenService.java
│   ├── OAuthTokenInterceptor.java
│   └── dto/
│       └── OAuthTokenResponse.java
│
├── client/                                  ← Client HTTP verso pagoPA
│   └── PiattaformaUnitariaClient.java
│
├── soap/                                    ← Endpoint SOAP lato SIL
│   ├── endpoint/
│   │   └── ReconciliationEndpoint.java
│   └── exception/
│       └── SoapFaultExceptionResolver.java
│
├── common/                                  ← Classi condivise
│   └── exception/
│       ├── PiattaformaAuthenticationException.java
│       └── PiattaformaCommunicationException.java
│
├── health/                                  ← Health check Actuator
│   ├── OAuthTokenHealthIndicator.java
│   └── PiattaformaUnitariaHealthIndicator.java
│
├── logging/                                 ← Marker e logging infrastrutturale
│   ├── JdbiSqlLogger.java
│   └── LogMarker.java
│
└── util/                                    ← Utility tecniche condivise
    ├── Constants.java
    ├── LogHelper.java
    └── Utilities.java
```

### Package `logging`

Contiene i componenti trasversali usati per classificare e scrivere i log tecnici del middleware.

| Classe | Scopo |
|--------|-------|
| `LogMarker.java` | Enum centralizzato dei marker SLF4J usati per categorizzare i flussi di log (`MONITORING`, `REST`, `SOAP_SERVER`, `SOAP_CLIENT`, `METHOD`, `DB_STATEMENT`, `DB_CONNECTION_POOL`) |
| `JdbiSqlLogger.java` | Logger Jdbi personalizzato che traccia query SQL, tempo di esecuzione, stato read-only della transazione e query lente |

### Package `util`

Contiene componenti riusabili e trasversali, da usare per evitare duplicazione di costanti e helper sparsi nel codice.

| Classe | Scopo |
|--------|-------|
| `Constants.java` | Contenitore centralizzato delle costanti applicative condivise (namespace, header, codici, chiavi, path, valori ricorrenti) |
| `LogHelper.java` | Utility reflection-based che converte `Method` in firme leggibili per i log, con diversi livelli di dettaglio |
| `Utilities.java` | Contenitore di metodi helper stateless e riusabili, richiamabili da piu' componenti applicativi |

**Convenzioni di utilizzo**:
- `Constants` deve contenere solo costanti condivise e semanticamente stabili; evitare di inserirvi valori temporanei o specifici di una singola classe
- `Utilities` deve ospitare solo logica tecnica riusabile e priva di stato; non deve diventare un contenitore di business logic eterogenea
- quando una costante o una utility e' usata da un solo componente, e' preferibile mantenerla vicino alla classe che la usa

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
- Registra un `MessageDispatcherServlet` mappato su `/pu/sil/soap/*`
- Questo servlet intercetta tutte le richieste HTTP POST con Content-Type `text/xml` verso quel path
- I singoli endpoint SOAP (annotati con `@Endpoint`) vengono rilevati automaticamente

**Nota tecnica importante**: `springline2-ws` è una libreria client SOAP (per chiamare servizi esterni). Per esporre endpoint SOAP server-side è necessaria la dipendenza separata `spring-boot-starter-web-services`, che è stata aggiunta esplicitamente al pom.

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

## 8. Modulo Endpoint SOAP

### `ReconciliationEndpoint.java`

**Tipo**: `@Endpoint` (Spring WS)  
**Scopo**: Riceve le richieste SOAP dai SIL e le inoltra alla Piattaforma Unitaria.

**Dettagli tecnici**:
- Namespace: `http://www.regione.veneto.it/pagamenti/pivot/ente/`
- Header namespace: `http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead`
- Local part: `pivotSILAutorizzaImportFlussoTesoreria`
- Path di ricezione: `/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati`

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

**Flusso interno dell'endpoint (proxy trasparente)**:
1. Spring WS intercetta la richiesta SOAP in base al namespace/localPart
2. L'endpoint riceve anche il `MessageContext`, da cui estrae il **SOAP Envelope completo** (Header + Body)
3. L'Envelope viene serializzato in stringa XML tramite `SoapMessage.writeTo()`
4. Chiama `piattaformaClient.forwardSoapRequest(path, envelopeXml)` — inoltra l'**intero Envelope**
5. Riceve la risposta SOAP dalla PU (anch'essa un Envelope completo)
6. Estrae il contenuto del `<Body>` dalla risposta con `extractBodyContent()`
7. Converte il body in `Element` e lo restituisce a Spring WS
8. Spring WS ri-avvolge l'Element in un nuovo SOAP Envelope e lo invia al SIL

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

## 9. Gestione degli Errori

### `SoapFaultExceptionResolver.java`

**Tipo**: `@Component` + `EndpointExceptionResolver`  
**Scopo**: Intercetta tutte le eccezioni non gestite negli endpoint SOAP e le converte in **SOAP Fault** strutturate, garantendo che i SIL ricevano sempre una risposta SOAP valida anche in caso di errore.

**Mapping delle eccezioni**:

| Eccezione | SOAP Fault | Codice errore |
|-----------|-----------|---------------|
| `PiattaformaAuthenticationException` | `Server/Receiver Fault` | `AUTH_ERROR` |
| `PiattaformaCommunicationException` | `Server/Receiver Fault` | `COMM_ERROR` |
| Qualsiasi altra `Exception` | `Server/Receiver Fault` | `INTERNAL_ERROR` |

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

## 10. Monitoraggio e Health Check

Il middleware espone endpoint di monitoraggio tramite **Spring Boot Actuator**.

Accanto agli endpoint Actuator, il progetto dispone di una infrastruttura di logging custom che si affianca al logging standard di Spring Boot e ai meccanismi di monitoraggio messi a disposizione da SpringLine2.

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

### Logging applicativo e tecnico

La strategia di logging del middleware e' divisa in due livelli complementari:

- **logging framework/runtime**: console, root logger, package logger e file gestiti da `logback-spring.xml`
- **logging semantico applicativo**: marker SLF4J centralizzati in `LogMarker.java`, riusati dai componenti per classificare i messaggi

### `LogMarker.java`

`LogMarker` centralizza i marker SLF4J del progetto in un unico enum, evitando stringhe duplicate o incoerenti nei logger applicativi.

| Marker enum | Nome marker | Uso previsto |
|-------------|-------------|-------------|
| `MONITORING` | `MON_GEN` | Eventi di monitoraggio generale |
| `REST` | `MON_REST` | Chiamate REST e integrazioni HTTP |
| `SOAP_SERVER` | `MON_WSS` | Tracciamento richieste SOAP ricevute dal middleware |
| `SOAP_CLIENT` | `MON_WSC` | Chiamate SOAP in uscita verso sistemi esterni |
| `METHOD` | `MON_METH` | Tracing di esecuzione di metodi applicativi |
| `DB_STATEMENT` | `MON_DBS` | Query SQL e tempi di esecuzione |
| `DB_CONNECTION_POOL` | `MON_CONN` | Eventi legati al pool di connessioni |

### `LogHelper.java`

`LogHelper` e' una utility tecnica che trasforma un oggetto `java.lang.reflect.Method` in una stringa leggibile per i log.

**Perche' serve**:
- rende i log piu' comprensibili quando il metodo e' ottenuto via reflection
- permette di scegliere un formato breve o dettagliato in base al contesto
- viene usata in particolare nel logging SQL Jdbi per indicare quale metodo applicativo ha originato una query

**Formati disponibili**:
- `methodToShortString(...)`: nome metodo con `(..)` se esistono parametri
- `methodToString(...)`: nome metodo con tipi dei parametri
- `methodToLongString(...)`: modificatore, tipo di ritorno, nome metodo e parametri
- `methodToFullString(...)`: rappresentazione completa con nomi fully-qualified dei tipi e della classe dichiarativa

### Logging SQL con `JdbiSqlLogger`

Il layer Jdbi e' predisposto per usare `JdbiSqlLogger` come logger SQL personalizzato.

**Informazioni tracciate**:
- metodo Java sorgente della query
- tempo di esecuzione in millisecondi
- SQL renderizzato
- binding dei parametri quando presenti
- stato read-only della transazione corrente
- evidenziazione delle query lente quando supera la soglia configurata

In caso di eccezione SQL, il logger emette anche lo stack trace associato alla query fallita.

### File di log generati

Il file `mypay.mypaycore-properties/src/main/resources/logback-spring.xml` configura i seguenti output:

| File | Ruolo |
|------|-------|
| `backend.log` | Log tecnico generale del backend |
| `mon.log` | Tracciamento sintetico di monitoraggio |
| `app.log` | Flusso applicativo dedicato |
| `audit.log` | Eventi di audit |
| `filter.log` | Request/response tracing e filtri |

### Nota di allineamento configurativo

La documentazione assume che ogni logger dedicato venga instradato verso il file semanticamente coerente con il proprio scopo. Se in futuro la configurazione `logback-spring.xml` verra' modificata, la guida dovra' essere riallineata per mantenere coerenza tra marker, logger e file di destinazione.

---

## 11. Resilienza

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

## 12. Ambienti e Profili

Il progetto utilizza attualmente **un solo profilo attivo**: `dev`. I profili `uat` e `prod` sono stati rimossi per semplificare l'ambiente di sviluppo; verranno ricreati quando necessario per il deployment in ambienti superiori.

Tutti i file di configurazione sono in formato **`.properties`** (la migrazione da `.yml` è avvenuta contestualmente alla semplificazione dei profili).

### Panoramica profili

| Profilo | Stato | Piattaforma target | Sicurezza | Logging |
|---------|-------|-------------------|-----------|---------|
| `dev` | **Attivo** | `api.uat.p4pa.pagopa.it` | JWT disabilitato (anonymous) | DEBUG |
| `uat` | Da creare | `api.uat.p4pa.pagopa.it` | JWT abilitato | INFO |
| `prod` | Da creare | URL da env var | JWT abilitato + segreti da env var | WARN |
| *(base)* | Sempre attivo | `api.uat.p4pa.pagopa.it` | JWT su `/pu/sil/soap/**` | INFO |

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
- SpringLine2 security: JWT richiesto su `/pu/sil/soap/**`, anonymous su Swagger e Actuator health
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

## 13. Test Unitari

Il progetto ha **22 test unitari** suddivisi in 3 classi, tutti con risultato BUILD SUCCESS.

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

### `ReconciliationEndpointTest` — 6 test

Testa l'endpoint SOAP di riconciliazione con approccio proxy trasparente.

| Test | Cosa verifica |
|------|---------------|
| `handleReconciliationRequest_forwardsFullEnvelopeAndReturnsResponse` | Flusso completo: estrae Envelope dal MessageContext, inoltra alla PU, estrae body dalla risposta |
| `handleReconciliationRequest_throwsRuntimeException_onClientError` | Eccezione dal client: rilancia come RuntimeException |
| `handleReconciliationRequest_usesCorrectServicePath` | Il path di inoltro è quello corretto (`/pu/sil/soap/reconciliation/...`) |
| `handleReconciliationRequest_forwardsFullEnvelopeXml` | L'XML inoltrato contiene l'Envelope completo (Header + Body), non solo il Body |
| `handleReconciliationRequest_extractsBodyFromPUResponse` | Il body estratto dalla risposta PU è corretto (solo contenuto del Body) |
| `constants_haveCorrectValues` | Le costanti di namespace e path hanno i valori corretti (Veneto) |

> **Nota**: I test mockano `MessageContext` e `SoapMessage` per simulare l'estrazione
> dell'Envelope SOAP completo, replicando il comportamento reale di Spring WS.

---

## 14. Come avviare il progetto

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

## 15. Come testare il flusso completo

### 15.1 Test con profilo `dev` (PU UAT reale) — TEST END-TO-END

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
POST http://localhost:8080/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati
Content-Type: text/xml;charset=UTF-8
```

Body:
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

## 16. Cosa NON è ancora implementato

Questa sezione è fondamentale per chi prende in carico il progetto: elenca esplicitamente le funzionalità **intenzionalmente escluse** dalle fasi completate finora.

| Funzionalità | Stato | Note |
|-------------|-------|------|
| Schema e tabelle del database PostgreSQL | Non implementato | Il DataSource e Jdbi sono configurati e funzionanti; mancano ancora le tabelle (`TRANSACTION_LOG`, `AUDIT_LOG`, ecc.) e i DAO/query Jdbi corrispondenti |
| Logica di business (riconciliazione, tesoreria) | Non implementata | L'endpoint attuale fa solo forwarding del payload |
| Trasformazione payload SOAP | Non implementata | Il payload viene inoltrato così com'è senza modifiche |
| Validazione business dei dati in ingresso | Non implementata | Spring WS valida solo il namespace/localPart |
| Endpoint SOAP aggiuntivi | Non implementati | Solo `pivotSILAutorizzaImportFlussoTesoreria` |
| Contract-first (WSDL/XSD) | Non implementato | Approccio contract-last corrente |
| Messaggistica asincrona (JMS/ActiveMQ) | Non implementata | `springline2-jms` commentato nel pom |
| Test di integrazione end-to-end | Non implementati | Solo unit test con mock |
| Multi-tenancy (più enti su stessa istanza) | Non valutata | Futura decisione architetturale |
| Rate limiting per SIL | Non implementato | Potrebbe essere necessario con più enti |

---

## 17. Prossimi passi — Fasi Future

### Fase 2 — Persistenza Database (plumbing completato ✅)

**Obiettivo originale**: Attivare la connessione al database e creare le tabelle necessarie per il middleware.

**Stato**: Il **plumbing del database e' completato** (Fase 2 completata). PostgreSQL e' configurato e connesso tramite `DataSourceConfiguration.java` con pool HikariCP; l'accesso ai dati e' basato su `JdbiConfiguration.java`. Il datasource `pa` e' attivo nel profilo `dev`.

**Lavoro rimanente**:
1. Definire lo schema PostgreSQL — creare script SQL in `mypay.mypaycore-db/src/main/sql/`:
   - Tabella `TRANSACTION_LOG` (traccia di ogni chiamata SIL → Piattaforma)
   - Tabella `AUDIT_LOG` (eventi di sicurezza, autenticazioni)
   - *(Opzionale)* Tabella `OAUTH_TOKEN_CACHE` per persistere il token OAuth2 tra riavvii
2. Creare DAO/repository Jdbi nel package applicativo dedicato all'accesso dati
3. Definire query SQL, mapper e modelli necessari per il logging transazionale e l'audit
4. Aggiungere credenziali PostgreSQL reali nel profilo `dev` in `application-dev.properties` (attualmente con placeholder)

**Decisioni da prendere**: naming convention delle tabelle, strategia di migrazione (Flyway? script manuali?), quali dati persistere.

---

### Fase 3 — Logica di Business

**Obiettivo**: Implementare la logica reale di riconciliazione e gestione flussi di tesoreria.

**Attività**:
1. Analisi e mappatura delle specifiche SOAP della Piattaforma Unitaria
2. Logica di riconciliazione pagamenti (`tipoFlusso=O`, `tipoFlusso=F`)
3. Trasformazione e validazione dei payload SOAP
4. Gestione degli stati di transazione
5. Integrazione con il log transazionale (Fase 2)

**Prerequisiti**: documentazione delle API della Piattaforma Unitaria, specifiche dei flussi di tesoreria.

---

### Fase 4 — Endpoint SOAP Aggiuntivi

**Obiettivo**: Aggiungere tutti gli endpoint SOAP richiesti dai SIL.

**Attività**:
1. Definizione WSDL/XSD per tutti gli endpoint
2. Valutazione migrazione da contract-last a contract-first
3. Implementazione nuovi `@Endpoint` Spring WS
4. Validazione XML Schema sulle richieste in ingresso

**Decisioni da prendere**: lista completa degli endpoint, se usare WSDL forniti da pagoPA o definirli internamente.

---

### Fase 6 — Messaggistica Asincrona

**Obiettivo**: Gestire operazioni asincrone tramite JMS/ActiveMQ.

**Attività**:
1. Riattivare `springline2-jms` nel pom
2. Configurare ActiveMQ (infrastruttura da definire con ARIA)
3. Definire le operazioni da rendere asincrone (notifiche, batch processing)
4. Implementare producer/consumer con dead letter queue

**Prerequisiti**: infrastruttura ActiveMQ disponibile, definizione dei casi d'uso asincroni.

---

## 18. Agenti OpenCode

Il progetto utilizza **OpenCode** come strumento di sviluppo assistito da AI. Gli agenti personalizzati sono definiti in `.opencode/agents/` e le regole globali per tutti gli agenti si trovano in `AGENTS.md` nella root del repository.

### Agenti disponibili

| Agente | File | Quando usarlo |
|--------|------|---------------|
| `@planner` | `.opencode/agents/planner.md` | Pianificare nuove fasi, aggiornare docs/, allineare Plan.md dopo modifiche al codice |

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
