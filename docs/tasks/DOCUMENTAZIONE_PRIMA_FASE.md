# DOCUMENTAZIONE_PRIMA_FASE
## Middleware MyPay — Guida Tecnica Completa

**Versione**: 1.0.0  
**Data**: Marzo 2026  
**Stato**: Prima fase completata — Fondazioni operative

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
12. [Mock della Piattaforma Unitaria](#12-mock-della-piattaforma-unitaria)
13. [Ambienti e Profili](#13-ambienti-e-profili)
14. [Test Unitari](#14-test-unitari)
15. [Come avviare il progetto](#15-come-avviare-il-progetto)
16. [Come testare il flusso completo](#16-come-testare-il-flusso-completo)
17. [Cosa NON è ancora implementato](#17-cosa-non-è-ancora-implementato)
18. [Prossimi passi — Fasi Future](#18-prossimi-passi--fasi-future)

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
       │  Header: Authorization: Bearer <token-SIL>
       │  Body: <soapenv:Envelope>...</soapenv:Envelope>
       ▼
┌─────────────────────────────────────────┐
│         MIDDLEWARE (questo progetto)    │
│                                         │
│  1. Valida il token Bearer del SIL      │
│  2. Estrae il payload SOAP              │
│  3. Ottiene token OAuth2 da pagoPA      │
│     (o lo usa dalla cache)              │
│  4. Inoltra la richiesta autenticata    │
│  5. Riceve la risposta                  │
│  6. Restituisce la risposta al SIL      │
└─────────────────────────────────────────┘
       │
       │  POST /pu/sil/soap/...
       │  Header: Authorization: Bearer <token-OAuth2>
       ▼
Piattaforma Unitaria (pagoPA)
       │
       ▼
     pagoPA
```

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
| `spring-boot-starter-data-jpa` | gestita da Spring Boot | JPA/Hibernate per accesso al database |
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
    │   │   ├── mock/
    │   │   └── soap/
    │   └── resources/config/
    │       ├── application.yml        ← configurazione base
    │       ├── application-local.yml  ← profilo sviluppo locale con mock
    │       ├── application-dev.yml    ← profilo sviluppo connesso a UAT
    │       ├── application-uat.yml    ← profilo User Acceptance Testing
    │       ├── application-prod.yml   ← profilo produzione
    │       └── bootstrap.yml
    └── test/
        ├── java/.../api/
        │   ├── auth/OAuthTokenServiceTest.java
        │   ├── client/PiattaformaUnitariaClientTest.java
        │   └── soap/endpoint/ReconciliationEndpointTest.java
        └── resources/config/application.yml
```

### `mypay.mypaycore-db` (modulo database)

Contiene gli script SQL per il database. Attualmente contiene solo `000_PLACEHOLDER.sql`. Il modulo è gestito dal plugin `custom-package-plugin` (ARIA) che produce un archivio ZIP con gli script per il deployment. Gli script reali verranno aggiunti quando si definirà lo schema del database PostgreSQL.

> **Nota**: Il tag `<summary>` è stato rimosso dalla configurazione del plugin perché non riconosciuto dalla versione `3.2.0` (causava un falso positivo in IntelliJ pur non compromettendo la build).

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
│   └── DataSourceConfig.java
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
└── mock/                                    ← Mock server (solo profilo local)
    └── MockPiattaformaUnitariaController.java
```

---

## 5. Modulo di Configurazione

### `PiattaformaUnitariaConfig.java`

**Tipo**: `@Configuration` + `@ConfigurationProperties(prefix = "piattaforma-unitaria")`  
**Scopo**: Centralizza tutti i parametri di connessione verso la Piattaforma Unitaria.

Questa classe legge automaticamente dal file `application.yml` il blocco:

```yaml
piattaforma-unitaria:
  base-url: https://api.uat.p4pa.pagopa.it
  auth:
    token-url: https://api.uat.p4pa.pagopa.it/pu/auth/oauth/token
    client-id: SELC_99999000013SIL_RegLomb2
    client-secret: xxxxx
    grant-type: client_credentials
    scope: openid
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

### `DataSourceConfig.java`

**Tipo**: `@Configuration` + `@EnableJpaRepositories`  
**Scopo**: Configura il DataSource PostgreSQL `pa` (database principale di MyPay) con pool HikariCP e JPA/Hibernate.

**Perché è necessaria**: Spring Boot auto-configura il datasource solo se le proprietà seguono il prefisso standard `spring.datasource.*`. Il progetto usa il prefisso personalizzato `spring.datasource.pa.*` (convenzione ereditata dal progetto mypay4 legacy), quindi è necessaria una classe di configurazione esplicita.

**Bean creati**:

| Bean | Tipo | Descrizione |
|------|------|-------------|
| `paDataSourceProperties` | `DataSourceProperties` | Legge `spring.datasource.pa.*` (url, username, password, driver) |
| `paDataSource` | `HikariDataSource` | Pool di connessioni; parametri da `spring.datasource.pa.hikari.*` |
| `paEntityManagerFactory` | `LocalContainerEntityManagerFactoryBean` | JPA EntityManager; scansiona il package base per entity `@Entity` |
| `paTransactionManager` | `PlatformTransactionManager` | Gestione transazioni JPA |

Tutti i bean sono annotati con `@Primary` poiché è il datasource unico dell'applicazione.

**Configurazione YAML corrispondente** (esempio profilo `local`):
```yaml
spring:
  datasource:
    pa:
      driver-class-name: org.postgresql.Driver
      url: jdbc:postgresql://10.199.144.62:5432/mypay4.pa
      username: mypay4.pa
      password: mypay4.pa
      hikari:
        minimum-idle: 1
        maximum-pool-size: 5
        pool-name: HikariPool-PA-local
  jpa:
    show-sql: true
    properties:
      hibernate:
        default_schema: mypay4
```

**Aggiungere entity in futuro**: quando si creeranno classi JPA (`@Entity`), inserirle nel package `it.ariaspa.mypay.mypaycore.api.domain` e i repository corrispondenti in `it.ariaspa.mypay.mypaycore.api.repository`.

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
        │     Header: Content-Type: application/x-www-form-urlencoded
        │     Body: client_id=...&client_secret=...&grant_type=client_credentials&scope=openid
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
- Namespace: `http://www.regione.lombardia.it/mypay/ente`
- Local part: `pivotSILAutorizzaImportFlussoTesoreria`
- Path di ricezione: `/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati`

**Richiesta attesa dai SIL**:
```xml
<soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:ppt="http://www.regione.lombardia.it/mypay/ppt"
    xmlns:ente="http://www.regione.lombardia.it/mypay/ente">

    <soapenv:Header>
        <ppt:intestazionePPT>
            <codIpaEnte>SELC_99999000013</codIpaEnte>
            <identificativoDominio>99999000013</identificativoDominio>
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

**Flusso interno dell'endpoint**:
1. Spring WS estrae il `<Body>` dalla SOAP Envelope e passa il payload come `Element` al metodo
2. L'endpoint converte l'`Element` in stringa XML
3. Chiama `piattaformaClient.forwardSoapRequest(path, xmlString)`
4. Riceve la risposta XML dalla piattaforma
5. Converte la stringa XML di risposta in `Element` e la restituisce
6. Spring WS avvolge l'Element in una SOAP Envelope e invia la risposta al SIL

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
        <fault:errorCode xmlns:fault="http://www.regione.lombardia.it/mypay/fault">
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
- Effettua una GET leggera verso la base URL (o `/mock/status` in profilo `local`)
- Timeout ridotti: connect 3s, read 5s
- `UP`: la piattaforma risponde
- `DOWN`: timeout, errore di rete, o risposta di errore

---

## 11. Resilienza

Il middleware implementa resilienza tramite **Resilience4j** applicato al metodo `PiattaformaUnitariaClient.forwardSoapRequest()`.

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

## 12. Mock della Piattaforma Unitaria

### `MockPiattaformaUnitariaController.java`

**Tipo**: `@RestController` + `@Profile("local")`  
**Scopo**: Simula la Piattaforma Unitaria all'interno dello stesso processo applicativo, permettendo di testare il flusso completo senza connessione a servizi esterni.

**Attivo**: SOLO quando il profilo Spring è `local`. In tutti gli altri profili questo bean non viene creato.

**Endpoint esposti** (tutti sotto `/mock`):

| Endpoint | Metodo | Content-Type | Descrizione |
|----------|--------|-------------|-------------|
| `/mock/status` | GET | application/json | Diagnostica: verifica che il mock sia attivo |
| `/mock/pu/auth/oauth/token` | POST | form-urlencoded → JSON | Simula il token OAuth2 |
| `/mock/pu/sil/soap/reconciliation/...` | POST | text/xml | Simula la risposta SOAP |

**Comportamento del mock OAuth2**:
- Accetta qualsiasi `client_id` e `client_secret` senza validazione
- Restituisce un token fittizio con `expires_in: 3600`
- Logga i parametri ricevuti per debugging

**Comportamento del mock SOAP**:
- Estrae `codIpaEnte` e `tipoFlusso` dal payload XML ricevuto
- Restituisce una risposta SOAP con `codiceEsito: 0` (successo)
- Include un `identificativoFlusso` generato casualmente (`MOCK-XXXXXXXX`)

**Esempio di risposta mock**:
```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:ente="http://www.regione.lombardia.it/mypay/ente">
    <soapenv:Header/>
    <soapenv:Body>
        <ente:pivotSILAutorizzaImportFlussoTesoreria_RPT_risposta>
            <codIpaEnte>SELC_99999000013</codIpaEnte>
            <tipoFlusso>O</tipoFlusso>
            <codiceEsito>0</codiceEsito>
            <descrizioneEsito>Operazione completata con successo (MOCK)</descrizioneEsito>
            <identificativoFlusso>MOCK-A3F7C2D1</identificativoFlusso>
        </ente:pivotSILAutorizzaImportFlussoTesoreria_RPT_risposta>
    </soapenv:Body>
</soapenv:Envelope>
```

---

## 13. Ambienti e Profili

Il progetto supporta cinque profili Spring, ognuno con una configurazione specifica.

### Panoramica profili

| Profilo | Attivazione | Piattaforma target | Sicurezza | Logging |
|---------|------------|-------------------|-----------|---------|
| `local` | Sviluppo senza rete esterna | Mock interno (localhost) | Disabilitata (tutto anonymous) | DEBUG |
| `dev` | Sviluppo con UAT reale | `api.uat.p4pa.pagopa.it` | JWT abilitato | DEBUG |
| `uat` | Test di accettazione | `api.uat.p4pa.pagopa.it` | JWT abilitato | INFO |
| `prod` | Produzione | URL da env var | JWT abilitato + segreti da env var | WARN |
| *(default)* | Base (esteso dagli altri) | `api.uat.p4pa.pagopa.it` | JWT su `/pu/sil/soap/**` | INFO |

---

### Profilo `local` (`application-local.yml`)

**Quando usarlo**: Sviluppo locale senza accesso alla Piattaforma Unitaria reale.

**Caratteristiche**:
- `piattaforma-unitaria.base-url` punta a `http://localhost:8080/mock`
- Il `MockPiattaformaUnitariaController` è attivo (simula OAuth2 + SOAP)
- Tutta la sicurezza SpringLine2 è disabilitata (`uri-matchers: /**` con anonymous)
- Logging a livello DEBUG su tutti i pacchetti del middleware
- Tutti gli endpoint Actuator sono esposti senza restrizioni (`include: "*"`)
- Resilience4j con parametri rilassati (circuit breaker apre all'80% su 5 chiamate)
- Il `PiattaformaUnitariaHealthIndicator` usa `/mock/status` come URL di health check

---

### Profilo `dev` (`application-dev.yml`)

**Quando usarlo**: Sviluppo che richiede connessione reale all'ambiente UAT di pagoPA.

**Caratteristiche**:
- Punta all'ambiente UAT reale (`api.uat.p4pa.pagopa.it`)
- Credenziali OAuth2 leggibili da `application-dev.yml` o env var
- Logging DEBUG per il codice del middleware e Spring WS
- Actuator health con dettagli sempre visibili
- Resilience4j con parametri rilassati per non ostacolare il debugging

---

### Profilo `uat` (`application-uat.yml`)

**Quando usarlo**: Test di accettazione prima della messa in produzione.

**Caratteristiche**:
- Credenziali OAuth2 **solo da variabili d'ambiente** (`${PIATTAFORMA_CLIENT_ID}`, `${PIATTAFORMA_CLIENT_SECRET}`)
- Logging INFO (meno verboso)
- Actuator health con dettagli visibili solo agli utenti autorizzati
- Parametri Resilience4j standard (ereditati dal profilo base)

---

### Profilo `prod` (`application-prod.yml`)

**Quando usarlo**: Ambiente di produzione.

**Caratteristiche**:
- URL base da variabile d'ambiente obbligatoria (`${PIATTAFORMA_BASE_URL}`)
- Tutte le credenziali da variabili d'ambiente — nessun valore di default
- Il segreto JWT SpringLine2 deve essere fornito via `${SPL_JWT_CYPHER_SECRET}`
- Actuator: solo `/actuator/health` e `/actuator/info` esposti, dettagli nascosti
- Logging root a WARN, Spring WS a ERROR (minimo indispensabile)
- Resilience4j più conservativo: finestra 20 chiamate, attesa OPEN 60s, 3 tentativi retry

**Variabili d'ambiente obbligatorie in produzione**:

| Variabile | Descrizione |
|-----------|-------------|
| `PIATTAFORMA_BASE_URL` | URL base della Piattaforma Unitaria |
| `PIATTAFORMA_CLIENT_ID` | Client ID OAuth2 |
| `PIATTAFORMA_CLIENT_SECRET` | Client secret OAuth2 |
| `SPL_JWT_CYPHER_SECRET` | Segreto per cifratura JWT SpringLine2 |

---

### Profilo base (senza suffisso — `application.yml`)

È la configurazione condivisa da tutti i profili. Ogni profilo specifico sovrascrive solo le proprietà che gli servono.

**Configurazioni principali**:
- JPA base: `ddl-auto: none`, `show-sql: false`, `open-in-view: false`
- Piattaforma target: `api.uat.p4pa.pagopa.it` (default UAT)
- Resilience4j: parametri standard
- Actuator: endpoints `health, info, metrics, circuitbreakers, retries`
- SpringLine2 security: JWT richiesto su `/pu/sil/soap/**`, anonymous su Swagger e Actuator health
- La configurazione del DataSource **non è nel profilo base** — ogni profilo dichiara la propria connessione

---

## 14. Test Unitari

Il progetto ha **19 test unitari** suddivisi in 3 classi, tutti con risultato BUILD SUCCESS.

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

### `ReconciliationEndpointTest` — 3 test

Testa l'endpoint SOAP di riconciliazione.

| Test | Cosa verifica |
|------|---------------|
| `shouldForwardRequestAndReturnResponse` | Flusso completo: riceve SOAP, inoltra, restituisce risposta |
| `shouldHandleExceptionFromClient` | Eccezione dal client: rilancia come RuntimeException |
| `shouldPreserveNamespacesInResponse` | Il namespace XML viene preservato nella risposta |

### Strategia di testabilità

`OAuthTokenService` e `PiattaformaUnitariaClient` hanno un **costruttore package-private** aggiuntivo che accetta un `RestTemplate` come parametro. Questo permette ai test di iniettare un `RestTemplate` mockato senza modificare il costruttore principale usato da Spring.

Il costruttore Spring è marcato con `@Autowired` per disambiguare (necessario perché esistono due costruttori).

---

## 15. Come avviare il progetto

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

### Avvio in modalità locale (consigliato per sviluppo)

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn spring-boot:run -f mypay.mypaycore-springboot/pom.xml -Denforcer.skip=true -Dspring-boot.run.profiles=local"
```

### Configurazione IntelliJ IDEA

1. **Run → Edit Configurations → + → Maven**
2. Configurare:
   - **Name**: `MyPayCore — local`
   - **Command**: `spring-boot:run -f mypay.mypaycore-springboot/pom.xml -Denforcer.skip=true -Dspring-boot.run.profiles=local`
   - **Working directory**: root del progetto (`mypay.mypaycore`)

### Note sul flag `-Denforcer.skip=true`

Il POM padre corporativo (`it.ariaspa:cm:1.0.0`) ha un plugin enforcer che verifica che il sistema operativo sia Unix. Poiché lo sviluppo avviene su Windows, questo flag è necessario per bypassare il controllo. **Non usarlo mai in ambienti CI/CD che girano su Linux**.

---

## 16. Come testare il flusso completo

Con l'applicazione avviata in profilo `local` su `http://localhost:8080`:

### Passo 1 — Verifica che il mock sia attivo

```
GET http://localhost:8080/mock/status
```
Risposta attesa:
```json
{
  "status": "UP",
  "profile": "local",
  "description": "Mock Piattaforma Unitaria attivo"
}
```

### Passo 2 — Health check del middleware

```
GET http://localhost:8080/actuator/health
```

### Passo 3 — Chiamata SOAP principale (simula un SIL)

Importare in **Postman**: `requests/MyPay-Middleware-Local.postman_collection.json`  
Importare in **SoapUI**: `requests/MyPay-Middleware-Local-soapui.xml`

Oppure chiamata diretta:

```
POST http://localhost:8080/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati
Content-Type: text/xml;charset=UTF-8
SOAPAction: pivotSILAutorizzaImportFlussoTesoreria
```

Body:
```xml
<soapenv:Envelope
    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:ppt="http://www.regione.lombardia.it/mypay/ppt"
    xmlns:ente="http://www.regione.lombardia.it/mypay/ente">
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

**Flusso interno che si attiva**:
```
Postman → ReconciliationEndpoint (Spring WS)
            → PiattaformaUnitariaClient.forwardSoapRequest()
                → OAuthTokenInterceptor.intercept()
                    → OAuthTokenService.getAccessToken()
                        → POST /mock/pu/auth/oauth/token  ← token fittizio
                → POST /mock/pu/sil/soap/...  ← risposta SOAP mock
            → risposta SOAP a Postman
```

---

## 17. Cosa NON è ancora implementato

Questa sezione è fondamentale per chi prende in carico il progetto: elenca esplicitamente le funzionalità **intenzionalmente escluse** dalle fasi completate finora.

| Funzionalità | Stato | Note |
|-------------|-------|------|
| Schema e tabelle del database PostgreSQL | Non implementato | Il DataSource è configurato e funzionante; mancano ancora le tabelle (`TRANSACTION_LOG`, `AUDIT_LOG`, ecc.) e le entity JPA corrispondenti |
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

## 18. Prossimi passi — Fasi Future

### Fase 2 — Persistenza Database (plumbing completato ✅)

**Obiettivo originale**: Attivare la connessione al database e creare le tabelle necessarie per il middleware.

**Stato**: Il **plumbing del database è completato** (Fase 2 completata). PostgreSQL è configurato e connesso tramite `DataSourceConfig.java` con pool HikariCP e JPA/Hibernate. Il datasource `pa` è attivo in tutti i profili.

**Lavoro rimanente**:
1. Definire lo schema PostgreSQL — creare script SQL in `mypay.mypaycore-db/src/main/sql/`:
   - Tabella `TRANSACTION_LOG` (traccia di ogni chiamata SIL → Piattaforma)
   - Tabella `AUDIT_LOG` (eventi di sicurezza, autenticazioni)
   - *(Opzionale)* Tabella `OAUTH_TOKEN_CACHE` per persistere il token OAuth2 tra riavvii
2. Creare entity JPA (`@Entity`) nel package `it.ariaspa.mypay.mypaycore.api.domain`
3. Creare repository Spring Data nel package `it.ariaspa.mypay.mypaycore.api.repository`
4. Aggiungere credenziali PostgreSQL reali nei profili `dev`, `uat`, `prod` (attualmente con placeholder)

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
