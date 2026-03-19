# Piano di Implementazione - Middleware MyPay (SIL ↔ Piattaforma Unitaria)

## Contesto

Il progetto è stato generato dall'archetype **SpringLine2** (ARIA S.p.A.) con codice demo (Car CRUD, FileIO, JWT, Example). È stato trasformato in un **middleware SOAP** che riceve richieste dai SIL (Sistemi Informativi Locali), si autentica tramite OAuth2 verso la Piattaforma Unitaria (pagoPA), e inoltra le richieste autenticate.

---

## Stato Attuale

| Fase | Stato | Descrizione |
|------|-------|-------------|
| Fase 1 | ✅ Completata | Fondazioni: pulizia demo, struttura middleware, OAuth2, endpoint SOAP |
| Fase 5 | ✅ Completata | Resilienza, gestione errori, health check, profili multi-ambiente, test unitari |
| Fase 2 | ✅ Completata (plumbing) | Persistenza su database PostgreSQL — DataSource, HikariCP e Jdbi configurati; schema e query applicative ancora da definire |
| Semplificazione Configurazione | ✅ Completata | Eliminazione profili uat/prod, conversione configurazione da YAML a Properties, solo profilo `dev` attivo |
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

> **Nota**: I profili `uat` e `prod` sono stati successivamente rimossi nella fase di "Semplificazione Configurazione". Attualmente è attivo solo il profilo `dev`.

| Profilo | Logging | Resilienza | Credenziali | Actuator Health Details | Stato |
|---------|---------|-----------|-------------|------------------------|-------|
| `dev` | DEBUG | Rilassata (soglia 80%, attesa 10s) | Da variabili d'ambiente o `.env` | `always` | **Attivo** |
| `uat` | INFO | Standard | Mix Properties/env vars | `when-authorized` | Rimosso (da ricreare) |
| `prod` | WARN | Conservativa (soglia 40%, attesa 60s) | Solo env vars | `never` | Rimosso (da ricreare) |

### 5.5 Test Unitari ✅

**Dipendenze aggiunte**: `spring-boot-starter-test`, `spring-ws-test`

| Classe di test | # Test | Copertura |
|---------------|--------|-----------|
| `OAuthTokenServiceTest` | 9 | Richiesta token, caching, refresh, gestione errori, validazione |
| `PiattaformaUnitariaClientTest` | 7 | Inoltro, retry su 401, errori HTTP, timeout, fallback circuit breaker |
| `ReconciliationEndpointTest` | 3 | Inoltro successo, gestione errori, preservazione namespace |
| **Totale** | **19** | **BUILD SUCCESS, 0 fallimenti** |

**Configurazione test**: `src/test/resources/config/application.properties` con cloud config disabilitato, autenticazione anonima, resilienza rilassata.

**Nota sulla testabilità**: `OAuthTokenService` e `PiattaformaUnitariaClient` hanno costruttori package-private che accettano `RestTemplate` per iniezione di mock nei test.

---

## Fase 2 - Persistenza Database ✅ (Plumbing completato)

**Obiettivo**: Configurare la connessione al database PostgreSQL e predisporre il layer JDBC/Jdbi.

### Attività completate ✅

1. Configurato il driver `postgresql` insieme alle dipendenze `spring-boot-starter-jdbc` e Jdbi (`jdbi3-spring5`, `jdbi3-sqlobject`, `jdbi3-stringtemplate4`) nel `pom.xml`
2. Creato `DataSourceConfiguration.java` — configurazione manuale HikariCP + `DataSourceTransactionManager` (`@Primary`), con lettura delle proprieta' `spring.datasource.pa.*`
3. Creato `JdbiConfiguration.java` — istanza `jdbiPa`, plugin Jdbi, row mapper e supporto SQL Object
4. Aggiunto blocco `spring.datasource.pa.*` con credenziali PostgreSQL nel profilo `dev`

### Lavoro rimanente (schema DB) ⬜

1. Creare script SQL in `mypay.mypaycore-db/src/main/sql/`:
   - Tabella `TRANSACTION_LOG` (log transazioni SIL ↔ Piattaforma)
   - Tabella `AUDIT_LOG` (audit eventi)
   - *(Opzionale)* Tabella `OAUTH_TOKEN_CACHE` (persistenza token tra riavvii)
2. Creare DAO/repository Jdbi e relativi SQL object nei package applicativi dedicati
3. Definire query SQL, row mapper e modelli per persistenza e audit
4. Configurare credenziali PostgreSQL reali nel profilo `dev` in `application-dev.properties`

### Decisioni da prendere:
- Schema DB e naming conventions
- Strategia di migrazione (Flyway? Script manuali?)
- Quali dati persistere vs. tenere solo in-memory

---

## Semplificazione Configurazione ✅

**Obiettivo**: Semplificare l'ambiente di sviluppo eliminando i profili non utilizzati e standardizzando il formato dei file di configurazione.

**Data**: Marzo 2026  
**Risultato**: `mvn compile` → BUILD SUCCESS | `mvn test` → 22 test, 0 fallimenti, 0 errori

### Attività completate ✅

#### Eliminazione profili uat e prod
- Rimossi i file `application-uat.yml` e `application-prod.yml` dal modulo `mypay.mypaycore-springboot`
- Rimosso il file `application-uat.yml` (non esisteva `application-prod.yml` separato nel modulo springboot)
- Unico profilo attivo rimasto: **`dev`**

#### Conversione da YAML a Properties
Tutti i file di configurazione sono stati migrati dal formato `.yml` al formato `.properties`:

| File eliminato (YAML) | File creato (Properties) | Modulo |
|-----------------------|--------------------------|--------|
| `application.yml` | `application.properties` | `mypay.mypaycore-springboot` |
| `application-dev.yml` | `application-dev.properties` | `mypay.mypaycore-springboot` |
| `application-uat.yml` | *(eliminato senza sostituzione)* | `mypay.mypaycore-springboot` |
| `application-prod.yml` | *(eliminato senza sostituzione)* | `mypay.mypaycore-springboot` |
| `bootstrap.yml` | `bootstrap.properties` | `mypay.mypaycore-springboot` |
| `config/application.yml` (test) | `config/application.properties` (test) | `mypay.mypaycore-springboot` |
| `application.yml` | `application.properties` | `mypay.mypaycore-properties` |
| `bootstrap.yml` | `bootstrap.properties` | `mypay.mypaycore-properties` |

#### Fix prefisso datasource
Nel modulo `mypay.mypaycore-properties`, il file `application.properties` usa correttamente il prefisso `spring.datasource.pa.*` (allineato con `DataSourceConfiguration.java`).

#### Aggiornamento script di avvio
Il file `mypay.mypaycore-properties/src/main/resources/startup.sh` è stato aggiornato con il flag `--spring.profiles.active=dev`.

#### Aggiornamento AGENTS.md
La tabella dei profili in `AGENTS.md` è stata aggiornata per riflettere che attualmente esiste **un solo profilo attivo** (`dev`); i profili `uat` e `prod` sono da creare.

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
- DataSource configurato manualmente con prefisso `spring.datasource.pa.*` tramite `DataSourceConfiguration.java`, integrato con `JdbiConfiguration.java` (Spring Boot non auto-configura datasource con prefissi personalizzati)
- File di configurazione in formato **`.properties`** (migrazione da `.yml` completata — vedi sezione "Semplificazione Configurazione")
- Profilo attivo per lo sviluppo: **`dev`** (i profili `uat` e `prod` sono stati rimossi e verranno ricreati al momento del deployment)
