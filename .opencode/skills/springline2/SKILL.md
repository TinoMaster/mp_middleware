# Skill: SpringLine2

**Versione documento**: 1.0.0  
**Versione SpringLine2 compatibile**: `2027.01.01` (Spring Boot 3.5.5)  
**Riferimento ufficiale**: ARIA-W8B1-LGD@91 v.1.18 (16/10/2025)  
**Scope progetto**: `mypay.mypaycore` — middleware SOAP ↔ OAuth2

---

## Indice rapido

1. [Dipendenze Maven](#dipendenze-maven)
2. [Context](#context)
3. [Logging](#logging)
4. [Security](#security)
5. [SpringLine2-WS (client SOAP)](#springline2-ws-client-soap)
6. [SpringLine2-Config (Consul + Conjur)](#springline2-config)
7. [SpringLine2-Data](#springline2-data)
8. [Provider REST](#provider-rest)
9. [Client REST](#client-rest)
10. [Hardening Tomcat — default](#hardening-tomcat)
11. [Insidie comuni](#insidie-comuni)
12. [Riferimento proprietà YAML](#riferimento-proprietà-yaml)

---

## Dipendenze Maven

`groupId` per tutti i moduli: `it.ariaspa.springline2`

| Modulo | Versione | Quando usarlo |
|--------|----------|---------------|
| `springline2-core` | `${spring-line.version}` | SEMPRE — context, logging, security base, REST client/provider |
| `springline2-data` | `${spring-line.version}` | Accesso DB con DataSource SpringLine2 (con monitoraggio tempi e reale/virtuale) |
| `springline2-jms` | `${spring-line.version}` | Messaggistica JMS/ActiveMQ Artemis |
| `springline2-openapi` | `${spring-line.version}` | Generazione spec OpenAPI/Swagger |
| `springline2-ws` | `2026.01.01` (**versione FISSA**) | Client SOAP con logging MON e propagazione SISS |
| `springline2-batch` | `${spring-line.version}` | Batch Spring Batch |
| `springline2-config` | `${spring-line.version}` | Configurazione da Consul + segreti da Conjur |

> ⚠️ **`springline2-ws` usa versione FISSA `2026.01.01`**, NON `${spring-line.version}`. Non cambiare.

```xml
<!-- Esempio dichiarazione nel pom.xml -->
<dependency>
    <groupId>it.ariaspa.springline2</groupId>
    <artifactId>springline2-core</artifactId>
</dependency>
<dependency>
    <groupId>it.ariaspa.springline2</groupId>
    <artifactId>springline2-ws</artifactId>
    <version>2026.01.01</version>
</dependency>
```

---

## Context

### Ciclo di vita

| Situazione | Comportamento |
|------------|---------------|
| Richiesta HTTP in arrivo | Context auto-inizializzato da SpringLine2 |
| Job Batch | Context **NON** auto-inizializzato — chiamare `ContextHolder.attach()` manualmente |
| Thread secondario | Propagare manualmente o usare `ContextHolder.attach()` |

### Accesso al Context

```java
// Metodo 1: statico
Context ctx = ContextHolder.currentContext();

// Metodo 2: iniezione nel controller/service (aggiungere @Parameter(hidden=true) nei controller)
@CtxContext Context context

// Da v2026.01.01 — attributi arbitrari
@CtxAttributes Map<String, Object> attributes
Map<String, Object> attrs = ContextHolder.currentContext().getAttributes();
attrs.put("chiaveApplicativa", valore);
```

### Tracer / Correlazione log

| Campo | Significato |
|-------|-------------|
| `traceId` | Immutabile per tutta la durata della transazione |
| `spanId` | Cambia per sotto-transazione; default `"0000000000000000"` |
| `idDc` nei log | Concatenazione `traceId + "," + spanId` |

```java
String traceId = ContextHolder.currentContext().getTracer().getTraceId();
String spanId  = ContextHolder.currentContext().getTracer().getSpanId();
```

---

## Logging

### Livelli disponibili

| Livello | Scopo | Trigger |
|---------|-------|---------|
| **Debug** | Log applicativo libero | `log.debug(...)` |
| **MON** | Monitoraggio HTTP in/out | Automatico per ogni richiesta/risposta |
| **MON-APP** | Monitoraggio applicativo custom | Codice applicativo via `MonExtra` / `AppExtra` |
| **AuditLog** | Sicurezza e compliance | Automatico + punti custom |

### Configurazione logback

| File | Quando usarlo |
|------|---------------|
| `logback-springline2.xml` | Default — file separati per tipo di log |
| `logback-springline2-ocp.xml` | OpenShift — tutto in console con prefissi `MON->`, `APP->`, `AUDIT->` |

```yaml
# application.yml
logging:
  config: classpath:logback-springline2.xml
  file:
    path: logs
```

### Arricchimento log MON

```java
// Accesso a MonExtra (campi tecnici: esito1, esito2, infoApp)
MonExtra monExtra = ContextHolder.currentContext().getLogInfo().getMonExtra();
monExtra.setEsito1("OPERAZIONE_OK");
monExtra.setEsito2("dettaglio");
monExtra.setInfoApp("info aggiuntiva");

// Accesso a AppExtra (payload business — MON-APP)
AppExtra appExtra = ContextHolder.currentContext().getLogInfo().getAppExtra();
appExtra.setPayload(myBusinessObject);
```

### Campi catturati automaticamente in MON

`timestamp`, `server`, `app`, `method+uri`, `idDc`, `user`, `ruolo`, `org`, `elapsed`, `tpDb`, `esito`

### AuditLog — eventi gestiti automaticamente

| Evento | Trigger |
|--------|---------|
| `app_startup` | Avvio applicazione |
| `app_shutdown` | Arresto applicazione |
| `authz_fail` | Fallimento autorizzazione |
| `authn_token_use` | Uso token autenticazione |
| `unexpected_exception` | Eccezione non gestita |
| `input_validation_fail` | Fallimento validazione input |

### Cifratura log (da v2025.02.01)

Usare `EncryptMsgConverter` + configurazione keystore in `bootstrap.yml`.

---

## Security

### Regola fondamentale — OBBLIGATORIA

> **Ogni volta che si personalizza la sicurezza Spring Security, è OBBLIGATORIO chiamare `securityFilter.addDefaultFilter(http)`.**

```java
@Configuration
public class SecurityConfig {

    @Autowired
    private SecurityFilterConfiguration securityFilter; // iniettare sempre

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        securityFilter.addDefaultFilter(http); // OBBLIGATORIO — mai omettere
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/public/**").permitAll()
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```

### Identity Provider disponibili

| Provider | Proprietà abilitazione |
|----------|------------------------|
| IAM | `spl.security.authentication.iam.enabled=true` |
| API Manager (x-jwt-assertion) | `spl.security.authentication.api-manager.enabled=true` |
| IDPC (SPID/CIE) | `spl.security.authentication.idpc.enabled=true` |
| SAMWEB | `spl.security.authentication.samweb.enabled=true` |
| SpringLine (propagazione v1.x) | `spl.security.authentication.springline.enabled=true` |
| Propagator | `spl.security.authentication.propagator.enabled=true` |
| **Anonymous** | `spl.security.authentication.anonymous.enabled=true` |
| JWT | `spl.security.authentication.jwt.enabled=true` |

> ⚠️ **Tutti i metodi di autenticazione sono DISABILITATI per default.** Se nessuno è abilitato → HTTP 403 per tutte le API.

### Anonymous auth (rilevante per endpoint SOAP di mypay.mypaycore)

```yaml
spl:
  security:
    authentication:
      anonymous:
        enabled: true
        uri-matchers:
          - /ws/**
          - /api/public/**
```

### Annotazioni utente (nei controller — aggiungere `@Parameter(hidden=true)`)

```java
@AuthUser   UserDetails user        // oggetto utente completo
@AuthName   String      username    // nome utente
@AuthFiscalNumber String cf         // codice fiscale
@AuthRole   String      role        // ruolo
```

### Formato Authorities

| Authority | Formato |
|-----------|---------|
| Tipo utente | `"USERTYPE_" + userType` |
| Circuito | `"CIRCUIT_" + circuit` |
| Ruolo | `"ROLE_" + role` |
| Metodo | `"METHOD_" + method` |
| Forza autenticazione | `"STRENGTH_" + strength` |
| Canale | `"CHANNEL_" + channel` |

### Accesso utente nel codice

```java
// Via ContextHolder
User user = ContextHolder.currentContext().getUser();

// Via annotazione nel controller
public ResponseEntity<?> myEndpoint(@Parameter(hidden=true) @AuthUser UserDetails user) { ... }
```

### StrictHttpFirewall (da v2023.06.01)

```yaml
spl:
  security:
    http-firewall:
      enabled: true
```

---

## SpringLine2-WS (client SOAP)

> ⚠️ **`springline2-ws` è una libreria CLIENT SOAP**, NON server.  
> Per esporre endpoint SOAP server-side, usare `spring-boot-starter-web-services` separatamente (già configurato in mypay.mypaycore).

### Versione

```xml
<dependency>
    <groupId>it.ariaspa.springline2</groupId>
    <artifactId>springline2-ws</artifactId>
    <version>2026.01.01</version>  <!-- VERSIONE FISSA — non cambiare -->
</dependency>
```

### Configurazione client SOAP

```java
@Configuration
public class SoapClientConfig {

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("it.ariaspa.mypay.generated"); // package classi JAXB
        return marshaller;
    }

    // WebServiceTemplate viene iniettato automaticamente da SpringLine2
    // con logging MON e propagazione SISS configurati
}
```

### Pattern client

```java
@Component
public class MioSoapClient extends WebServiceGatewaySupport {

    public RispostaType chiamata(RichiestaType richiesta) {
        return (RispostaType) getWebServiceTemplate()
            .marshalSendAndReceive("https://endpoint-url/ws", richiesta);
    }
}
```

### Proprietà `spl.client-ws.*`

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `spl.client-ws.autoconfiguration.enabled` | `true` | Abilita auto-configurazione |
| `spl.client-ws.logging.mon.enabled` | `true` | Log MON chiamate SOAP |
| `spl.client-ws.logging.app.enabled` | `false` | Log MON-APP payload SOAP |
| `spl.client-ws.propagation.siss.enabled` | `false` | Propagazione header SISS |

---

## SpringLine2-Config

Gestisce la configurazione da **HashiCorp Consul** (key/value) e i segreti da **CyberArk Conjur**.

### Struttura configurazione in Consul

```
config-server/<nome-app>/application.yml
```

> ⚠️ `"application"` è parola riservata — non può essere usata come nome applicazione.

### Configurazione connessione (in `bootstrap.yml`)

```yaml
# bootstrap.yml — caricato PRIMA di application.yml
spring:
  cloud:
    consul:
      host: consul.example.com
      port: 8500
      config:
        enabled: true
        prefix: config-server
```

### Riferimento segreti Conjur nel YAML

```yaml
# Nel file in Consul
spring:
  datasource:
    password: conjur:/db/database1/password  # riferimento a segreto Conjur
```

### Segreti con rotazione

```java
// Per segreti con rotazione — usare Environment.getProperty(), NON @Value
@Autowired
private Environment environment;

public String getPassword() {
    return environment.getProperty("spring.datasource.password"); // aggiornato dinamicamente
}
```

### Cache locale (da v2025.03.01)

```yaml
spl:
  consul:
    cache:
      enabled: true  # default true — evita troppe chiamate a Consul
```

### Aggiornamento dinamico configurazione

```java
@RefreshScope  // richiesto sui bean che devono reagire agli aggiornamenti
@Service
public class MioService {
    @Value("${mia.proprieta}")
    private String miaProprieta;
    // si aggiorna automaticamente quando la config cambia in Consul
}
```

---

## SpringLine2-Data

### Configurazione DataSource

> ⚠️ In `mypay.mypaycore` il DataSource è configurato **manualmente** con prefisso `spring.datasource.pa.*`  
> tramite `DataSourceConfiguration.java` e `JdbiConfiguration.java` — NON usa il prefisso `spl.datasource.*` di SpringLine2.

```yaml
# Configurazione SpringLine2-Data standard (NON usata in mypay.mypaycore)
spl:
  datasource:
    reale-virtuale: false  # default — rilevante per settore sanitario
    logging:
      execution-time: false  # default — monitoraggio tempi query DB
```

### DataSource Reale/Virtuale (settore sanitario)

| Modalità | Proprietà | Descrizione |
|----------|-----------|-------------|
| Singolo DataSource | `spl.datasource.reale-virtuale=false` | Default |
| Reale + Virtuale | `spl.datasource.reale-virtuale=true` | Due DataSource distinti per settore sanitario |

### Monitoraggio tempi DB

```yaml
spl:
  datasource:
    logging:
      execution-time: true  # abilita log tempo esecuzione query (incluso in MON come tpDb)
```

---

## Provider REST

### Costruzione risposte

```java
// Risposta con body
return RestResponse.ok(myBody);

// Risposta senza body
return RestResponse.noContent();

// Con builder
return RestResponseBuilder.status(HttpStatus.CREATED).body(myBody).build();
```

### Eccezioni SpringLine2

Le seguenti eccezioni vengono intercettate automaticamente da `GlobalExceptionHandler`  
(che gestisce anche la registrazione AuditLog):

| Eccezione | HTTP Status |
|-----------|-------------|
| `ForbiddenException` | 403 |
| `NotFoundException` | 404 |
| `ValidationException` | 400 |
| `ConflictException` | 409 |
| `UnauthorizedException` | 401 |

```java
// Utilizzo
if (!hasPermission) {
    throw new ForbiddenException("Accesso negato all'operazione richiesta");
}
```

### GlobalExceptionHandler

> Abilitato di default (`spl.error.exception-handler.enabled=true`).  
> Gestisce AuditLog automaticamente (`spl.error.exception-handler.auditlog=true`).  
> NON disabilitare a meno che non sia strettamente necessario.

---

## Client REST

### Auto-configurazione

Il `RestTemplateBuilder` è auto-configurato da SpringLine2 — nessuna configurazione speciale necessaria.

```java
@Service
public class MioRestClient {

    private final RestTemplate restTemplate;

    public MioRestClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build(); // già configurato con logging MON
    }

    public RispostaDto chiamata(String url) {
        return restTemplate.getForObject(url, RispostaDto.class);
    }
}
```

### Proprietà client REST

| Proprietà | Default | Descrizione |
|-----------|---------|-------------|
| `spl.client-rest.autoconfiguration.enabled` | `true` | Auto-configurazione RestTemplate |
| `spl.client-rest.logging.mon.enabled` | `true` | Log MON chiamate REST |
| `spl.client-rest.retry.enabled` | `false` | Abilita retry automatico |
| `spl.client-rest.retry.max-retries` | `3` | Numero massimo tentativi |

---

## Hardening Tomcat

Queste proprietà sono impostate da SpringLine2 per default — **non modificare** a meno che non ci sia una ragione esplicita:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health      # solo /health esposto di default
  endpoint:
    shutdown:
      access: none           # shutdown via HTTP disabilitato

server:
  servlet:
    context-path: /          # NON modificare mai
  error:
    whitelabel:
      enabled: true
    include-exception: false
    include-stacktrace: never
  max-http-request-header-size: 2MB
  tomcat:
    max-http-form-post-size: 2MB
    connection-timeout: 5s
```

---

## Insidie comuni

| # | Insidia | Soluzione |
|---|---------|-----------|
| 1 | Personalizzare `SecurityFilterChain` senza chiamare `securityFilter.addDefaultFilter(http)` | **Sempre** iniettare `SecurityFilterConfiguration` e chiamare il metodo |
| 2 | Usare `${spring-line.version}` per `springline2-ws` | Usare versione fissa `2026.01.01` |
| 3 | Tutti gli endpoint rispondono 403 | Nessun Identity Provider abilitato — abilitare almeno Anonymous o un provider |
| 4 | Usare `@Value` per segreti Conjur con rotazione | Usare `Environment.getProperty()` per leggere il valore aggiornato |
| 5 | Usare `"application"` come nome applicazione in Consul | Nome riservato — scegliere un altro nome |
| 6 | Aspettarsi che `springline2-ws` esponga endpoint SOAP | È solo CLIENT — per server SOAP usare `spring-boot-starter-web-services` |
| 7 | Dimenticare `@RefreshScope` su bean che usano config dinamica | Aggiungere `@RefreshScope` ai bean che devono reagire ad aggiornamenti Consul |
| 8 | Inizializzare Context in un job Batch | Chiamare `ContextHolder.attach()` manualmente — non è auto-inizializzato |
| 9 | Modificare `server.servlet.context-path` | Non modificare mai — valore `/` è gestito da SpringLine2 |
| 10 | Usare `WsConfigurerAdapter` (deprecato) | Usare l'interfaccia `WsConfigurer` |

---

## Riferimento proprietà YAML

### Proprietà complete con default

```yaml
# === CLOUD CONFIG ===
spring:
  cloud:
    config:
      enabled: false              # SpringLine2-Config disabilitato di default

# === LOGGING ===
logging:
  config: classpath:logback-springline2.xml
  file:
    path: logs

# === ERROR HANDLING ===
spl:
  error:
    exception-handler:
      enabled: true               # GlobalExceptionHandler abilitato
      auditlog: true              # AuditLog su eccezioni abilitato

  # === SECURITY ===
  security:
    generated-dev-password:
      enabled: false              # password dev generata disabilitata
    http-firewall:
      enabled: false              # StrictHttpFirewall disabilitato di default
    authentication:
      anonymous:
        enabled: false            # tutti i provider disabilitati di default
        uri-matchers: []
      iam:
        enabled: false
      api-manager:
        enabled: false
      jwt:
        enabled: false

  # === HTTP LOGGING ===
  http:
    logging:
      mon:
        enabled: true             # log MON richieste HTTP abilitato
      app:
        enabled: false            # log MON-APP payload disabilitato
        excluded-content-types:
          - application/x-www-form-urlencoded
          - multipart/form-data

  # === CLIENT REST ===
  client-rest:
    autoconfiguration:
      enabled: true
    logging:
      mon:
        enabled: true
    retry:
      enabled: false
      max-retries: 3

  # === CLIENT SOAP ===
  client-ws:
    autoconfiguration:
      enabled: true
    propagation:
      siss:
        enabled: false
    logging:
      mon:
        enabled: true
      app:
        enabled: false

  # === DATASOURCE (SpringLine2-Data) ===
  datasource:
    reale-virtuale: false
    logging:
      execution-time: false

  # === CONSUL CACHE ===
  consul:
    cache:
      enabled: true               # da v2025.03.01

# === JMS (SpringLine2-JMS) ===
spring:
  artemis:
    embedded:
      enabled: false
    mode: NATIVE

# === MANAGEMENT ===
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    shutdown:
      access: none

# === SERVER ===
server:
  servlet:
    context-path: /               # NON modificare
  error:
    whitelabel:
      enabled: true
    include-exception: false
    include-stacktrace: never
  max-http-request-header-size: 2MB
  tomcat:
    max-http-form-post-size: 2MB
    connection-timeout: 5s
```

---

## Contesto specifico mypay.mypaycore

| Aspetto | Scelta in mypay.mypaycore | Note |
|---------|--------------------------|------|
| DataSource | Configurazione manuale `DataSourceConfiguration.java` + `JdbiConfiguration.java` | Prefisso `spring.datasource.pa.*`, NON `spl.datasource.*` |
| SOAP server | `spring-boot-starter-web-services` | `springline2-ws` usato solo come CLIENT verso pagoPA |
| Autenticazione | Anonymous per endpoint `/ws/**` | I SIL chiamano via SOAP senza token |
| Profili | Solo `dev` attivo; `uat` e `prod` da creare | Profilo `local` rimosso intenzionalmente |
| Logging | `logback-springline2.xml` | File separati per tipo di log |
| Config | `application-{profilo}.yml` in `mypay.mypaycore-properties/` | Consul non ancora configurato |
