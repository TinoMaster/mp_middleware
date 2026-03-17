# Piano di Implementazione - Middleware MyPay (SIL ↔ Piattaforma Unitaria)

## Contesto

Il progetto è stato generato dall'archetype **SpringLine2** (ARIA S.p.A.) con codice demo (Car CRUD, FileIO, JWT, Example). È stato trasformato in un **middleware SOAP** che riceve richieste dai SIL (Sistemi Informativi Locali), si autentica tramite OAuth2 verso la Piattaforma Unitaria (pagoPA), e inoltra le richieste autenticate.

---

## Stato Attuale

| Fase | Stato | Descrizione |
|------|-------|-------------|
| Fase 1 | ✅ Completata | Fondazioni: pulizia demo, struttura middleware, OAuth2, endpoint SOAP |
| Fase 5 | ✅ Completata | Resilienza, gestione errori, health check, profili multi-ambiente, test unitari |
| Fase 2 | ⬜ Da fare | Persistenza su database (Oracle) |
| Fase 3 | ⬜ Da fare | Logica di business (riconciliazione, flussi tesoreria) |
| Fase 4 | ⬜ Da fare | Endpoint SOAP aggiuntivi, contract-first con WSDL/XSD |
| Fase 6 | ⬜ Da fare | Messaggistica asincrona (JMS/ActiveMQ) |

---

## Fase 1 - Fondazioni ✅

**Obiettivo**: Creare la struttura base del middleware senza logica di business, senza DB, senza test.

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
├── Application.java                          (esistente, invariato)
├── config/
│   ├── PiattaformaUnitariaConfig.java       (@ConfigurationProperties per OAuth2 e URL piattaforma)
│   └── SoapWebServiceConfig.java            (@EnableWs + MessageDispatcherServlet su /pu/sil/soap/*)
├── auth/
│   ├── dto/
│   │   └── OAuthTokenResponse.java          (DTO risposta token OAuth2)
│   ├── OAuthTokenService.java               (login OAuth2 Client Credentials, cache in-memory, ReentrantLock)
│   └── OAuthTokenInterceptor.java           (ClientHttpRequestInterceptor con Bearer token)
├── soap/
│   ├── endpoint/
│   │   └── ReconciliationEndpoint.java      (@Endpoint per pivotSILAutorizzaImportFlussoTesoreria)
│   └── exception/
│       └── SoapFaultExceptionResolver.java  (gestione globale SOAP Fault) [Fase 5]
├── client/
│   └── PiattaformaUnitariaClient.java       (RestTemplate + OAuth interceptor, retry su 401)
├── common/
│   └── exception/
│       ├── PiattaformaAuthenticationException.java
│       └── PiattaformaCommunicationException.java  [Fase 5]
└── health/                                   [Fase 5]
    ├── OAuthTokenHealthIndicator.java
    └── PiattaformaUnitariaHealthIndicator.java
```

### 1.3 Dipendenze aggiunte ✅

| Dipendenza | Motivo |
|-----------|--------|
| `spring-boot-starter-web-services` | Server SOAP (Spring WS @Endpoint) |
| `jakarta.xml.bind-api` | Marshalling/unmarshalling XML |
| `jaxb-runtime` | Implementazione JAXB per Jakarta |

Commentate (da riattivare in Fase 2): `springline2-data`, `ojdbc11`, `HikariCP`.

### 1.4 Configurazione ✅

- `application.yml` riscritto: rimossa config demo, aggiunto blocco `piattaforma-unitaria`, sicurezza per endpoint SOAP, DataSource auto-config disabilitata
- `local-properties/application.yml` allineato
- `mypay.mypaycore-db/`: script SQL demo eliminati, creato `000_PLACEHOLDER.sql`

### 1.5 Documentazione ✅

- `docs/architettura/ARCHITETTURA_MIDDLEWARE.md` — documento architetturale completo in italiano

---

## Fase 5 - Resilienza, Error Handling, Health, Profili, Test ✅

**Obiettivo**: Rendere il middleware robusto, monitorabile, testato e pronto per ambienti multipli.

> Nota: La Fase 5 è stata anticipata rispetto alle Fasi 2-4 per consolidare le fondamenta prima di aggiungere logica di business.

### 5.1 Resilienza (Resilience4j) ✅

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

**Applicazione**: `@CircuitBreaker` e `@Retry` su `PiattaformaUnitariaClient.forwardSoapRequest()` con metodo fallback.

### 5.2 Gestione Errori ✅

- **`SoapFaultExceptionResolver`**: Mappa eccezioni in SOAP Fault con codici:
  - `AUTH_ERROR` → `PiattaformaAuthenticationException`
  - `COMM_ERROR` → `PiattaformaCommunicationException`
  - `INTERNAL_ERROR` → eccezioni generiche
- **`PiattaformaCommunicationException`**: Nuova eccezione con campo `httpStatus` per errori HTTP/rete
- **Timeouts configurati**: OAuthTokenService (connect=5s, read=10s), PiattaformaUnitariaClient (connect=5s, read=30s)
- **Sicurezza XXE**: `ReconciliationEndpoint` hardened con `DocumentBuilderFactory` e `TransformerFactory` sicuri (DTD/external entities/XInclude disabilitati)

### 5.3 Health Check (Spring Actuator) ✅

**Dipendenza aggiunta**: `spring-boot-starter-actuator`

| Health Indicator | Cosa verifica |
|-----------------|---------------|
| `OAuthTokenHealthIndicator` | Token OAuth2 in cache valido (non scaduto) |
| `PiattaformaUnitariaHealthIndicator` | Connettività verso la Piattaforma Unitaria (HTTP GET leggero) |

**Endpoint esposti**: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/circuitbreakers`, `/actuator/retries`

### 5.4 Profili Multi-Ambiente ✅

| Profilo | Logging | Resilienza | Credenziali | Actuator Health Details |
|---------|---------|-----------|-------------|------------------------|
| `dev` | DEBUG | Rilassata (soglia 80%, attesa 10s) | Placeholder in YAML | `always` |
| `uat` | INFO | Standard | Mix YAML/env vars | `when-authorized` |
| `prod` | WARN | Conservativa (soglia 40%, attesa 60s) | Solo env vars | `never` |

### 5.5 Test Unitari ✅

**Dipendenze aggiunte**: `spring-boot-starter-test`, `spring-ws-test`

| Classe di test | # Test | Copertura |
|---------------|--------|-----------|
| `OAuthTokenServiceTest` | 9 | Richiesta token, caching, refresh, gestione errori, validazione |
| `PiattaformaUnitariaClientTest` | 7 | Inoltro, retry su 401, errori HTTP, timeout, fallback circuit breaker |
| `ReconciliationEndpointTest` | 3 | Inoltro successo, gestione errori, preservazione namespace |
| **Totale** | **19** | **BUILD SUCCESS, 0 fallimenti** |

**Configurazione test**: `src/test/resources/config/application.yml` con cloud config disabilitato, autenticazione anonima, resilienza rilassata.

**Nota sulla testabilità**: `OAuthTokenService` e `PiattaformaUnitariaClient` hanno costruttori package-private che accettano `RestTemplate` per iniezione di mock nei test.

---

## Fase 2 - Persistenza Database ⬜ (Da Fare)

**Obiettivo**: Riattivare il modulo DB con Oracle, creare tabelle per token e transazioni.

### Attività previste:
1. Riattivare dipendenze commentate: `springline2-data`, `ojdbc11`, `HikariCP`
2. Rimuovere esclusione DataSource da `application.yml`
3. Creare schema DB:
   - Tabella `OAUTH_TOKEN_CACHE` (persistenza token tra riavvii)
   - Tabella `TRANSACTION_LOG` (log transazioni SIL ↔ Piattaforma)
   - Tabella `AUDIT_LOG` (audit eventi)
4. Creare entity JPA e repository SpringLine2
5. Configurare connessione Oracle per profili dev/uat/prod

### Decisioni da prendere:
- Schema DB e naming conventions
- Strategia di migrazione (Flyway? Script manuali?)
- Quali dati persistere vs. tenere solo in-memory

---

## Fase 3 - Logica di Business ⬜ (Da Fare)

**Obiettivo**: Implementare la logica di riconciliazione e flussi di tesoreria.

### Attività previste:
1. Mapping dettagliato dei messaggi SOAP SIL → Piattaforma Unitaria
2. Logica di riconciliazione pagamenti
3. Gestione flussi di tesoreria (import/export)
4. Validazione business dei dati in ingresso
5. Trasformazione payload SOAP (se formati diversi tra SIL e Piattaforma)

### Decisioni da prendere:
- Specifiche esatte dei messaggi SOAP della Piattaforma Unitaria
- Regole di riconciliazione
- Formati flussi tesoreria

---

## Fase 4 - Endpoint SOAP Aggiuntivi ⬜ (Da Fare)

**Obiettivo**: Aggiungere gli endpoint SOAP mancanti, eventualmente migrare a contract-first.

### Attività previste:
1. Definizione WSDL/XSD per tutti gli endpoint
2. Migrazione da contract-last a contract-first (generazione classi da WSDL)
3. Implementazione endpoint aggiuntivi oltre alla riconciliazione
4. Validazione XML schema sulle richieste in ingresso

### Decisioni da prendere:
- Lista completa degli endpoint SOAP richiesti
- Se migrare a contract-first o restare contract-last
- WSDL/XSD forniti da pagoPA o da definire internamente

---

## Fase 6 - Messaggistica Asincrona ⬜ (Da Fare)

**Obiettivo**: Integrare messaggistica JMS per operazioni asincrone.

### Attività previste:
1. Configurare `springline2-jms` con ActiveMQ
2. Definire code per operazioni asincrone (notifiche, batch processing)
3. Implementare producer/consumer per flussi che non richiedono risposta sincrona
4. Dead letter queue per messaggi falliti

### Decisioni da prendere:
- Quali operazioni rendere asincrone
- Infrastruttura ActiveMQ (gestita da ARIA? Self-hosted?)
- Strategia di retry per messaggi JMS

---

## Note Tecniche

### Compilazione (ambiente Windows/WSL)
```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn compile -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

### Esecuzione test
```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn test -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

### Vincoli noti
- Il parent POM corporate (`it.ariaspa:cm:1.0.0`) ha enforcer plugin che richiede OS Unix → usare `-Denforcer.skip=true` su Windows
- `WsConfigurerAdapter` deprecato in Spring WS bundled con Spring Boot 3.5.5 → usato interfaccia `WsConfigurer`
- `springline2-ws` è una libreria client SOAP, non server → aggiunto `spring-boot-starter-web-services` separatamente
- DataSource auto-configuration esclusa esplicitamente finché le dipendenze DB sono commentate
