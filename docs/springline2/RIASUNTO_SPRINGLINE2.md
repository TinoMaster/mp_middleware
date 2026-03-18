Guida Tecnica SpringLine 2: Architettura, Moduli e Standard ARIA S.p.A.

1. Visione d'Insieme e Filosofia Architetturale

SpringLine 2 segna un cambiamento di paradigma fondamentale nell'ecosistema ARIA S.p.A., evolvendo dalla precedente impostazione "Starter Spring Boot" a una collezione di librerie modulari indipendenti. Questo passaggio è dettato dalla necessità di superare i limiti di un framework monolitico, consentendo una gestione del ciclo di vita del software basata sul versionamento atomico dei componenti.

L'approccio modulare permette agli sviluppatori di importare esclusivamente le funzionalità necessarie, evitando il "dependency hell" e riducendo l'impronta applicativa. In linea con gli standard aziendali, SpringLine 2 garantisce che le applicazioni siano "future-proof", isolate dall'evoluzione dei singoli framework sottostanti e resilienti ai cambiamenti tecnologici.

Punti di Forza:

* Flessibilità e Controllo: Integrazione granulare dei moduli e controllo totale sulle configurazioni.
* Sicurezza Compliance: Implementazione nativa dei protocolli IAM, IDPC e SAMWEB secondo gli standard regionali.
* Interoperabilità: Facilità di comunicazione tra sistemi eterogenei e versioni diverse della libreria.
* Manutenzione Indipendente: Possibilità di aggiornare un singolo modulo (es. springline2-data) senza dover ricertificare l'intero starter.


--------------------------------------------------------------------------------


2. Struttura Modulare e Gestione delle Dipendenze

La scomposizione di SpringLine 2 prevede moduli distinti per ambito funzionale. È responsabilità del Senior Architect definire quali componenti includere nel pom.xml per rispettare i requisiti di business e le policy di sicurezza.

Modulo	Artifact ID	Descrizione
Core	springline2-core	Pilastro centrale: Context, Logging, Security, API Rest e Cloud Config.
Data	springline2-data	Estensione JPA per DataSource Reali/Virtuali e monitoraggio performance.
JMS	springline2-jms	Integrazione ActiveMQ con logging MON/MON-APP avanzato.
OpenAPI	springline2-openapi	Documentazione Swagger-UI e specifiche OpenAPI 3.0 integrate.
WS	springline2-ws	Client SOAP con supporto alla propagazione per servizi SISS.
Batch	springline2-batch	Supporto ai processi massivi con gestione automatica del contesto.

Nota: Il groupId per tutti i moduli è it.ariaspa.springline2. Per il dettaglio delle versioni stabili, fare riferimento all'Appendice A del documento sorgente (es. versione 2024.02.01 per Spring Boot 3.2.2).


--------------------------------------------------------------------------------


3. Modulo Core: Gestione del Contesto (Context)

Il Context è il cuore pulsante dell'architettura; gestisce e rende accessibili le informazioni di transazione in modo trasversale ai bean Spring.

Funzionamento e Tracer Logic

Il sistema inizializza automaticamente un contesto per ogni transazione HTTP. Nei processi Batch, l'inizializzazione non è automatica e deve essere gestita tramite il ContextBatchListener. La tracciabilità è garantita dall'oggetto Tracer:

* traceId: Identificativo immutabile della transazione principale.
* spanId: Identificativo per transazioni secondarie, inizializzato di default a "0000000000000000". Ad ogni nuova operazione secondaria, lo spanId viene rigenerato mantenendo il traceId originale.

Accesso al Contesto

L'accesso può avvenire manualmente tramite ContextHolder o per iniezione via annotation:

// Accesso manuale (necessario in scenari non gestiti o multi-thread)
public ResponseEntity<Void> manualContextExample() {
    ContextHolder.attach(); // Inizializzazione forzata se assente
    Context ctx = ContextHolder.currentContext();
    return RestResponse.noContent();
}

// Accesso via Annotation
public ResponseEntity<Void> annotationExample(@Parameter(hidden = true) @CtxContext Context context) {
    return RestResponse.ok(context.getUser().getUsername());
}


Informazioni Recuperabili:

* User: Dati completi sull'identità e autorità.
* LogInfo: Metadati per i sistemi di tracciamento.
* Tracer: Identificativi traceId e spanId.
* Log-Out Url: Disponibile esclusivamente per i sistemi di autenticazione SAMWEB e IDPC.


--------------------------------------------------------------------------------


4. Sistema di Logging e Tracciabilità

SpringLine 2 impone un logging strutturato suddiviso in quattro livelli, fondamentale per la diagnosi in ambienti di produzione complessi.

Log di Monitoraggio (MON e MON-APP)

Tutte le interazioni (HTTP, Client Rest, JMS, WS) tracciano un set di proprietà standard. Il legame tra i log è l'identificativo idDc, ovvero la concatenazione di traceId e spanId.

Proprietà Standard Tracciate:

* timestamp: System millisecondi.
* server / app: Hostname e nome applicazione.
* idDc: Identificativo univoco di transazione (TraceId,SpanId).
* user / ruolo / str: Username, Ruolo e Organizzazione dell'utente.
* elapsed: Tempo totale di esecuzione in ms.
* tpDb: Tempo di esecuzione query SQL (timeDB nel contesto).

Customizzazione: Gli sviluppatori possono arricchire i log tramite gli oggetti MonExtra (per proprietà tecniche come esito1, esito2, infoApp) e AppExtra (per il monitoraggio applicativo dei payload).

AuditLog: Gestione Eventi Critici

Mentre SpringLine 2 automatizza la cattura di molti eventi, lo sviluppatore è responsabile del popolamento dei dettagli di business.

Evento	Nodo Id	Descrizione / Status
Startup/Shutdown	app_startup / app_shutdown	Registra il ciclo di vita dell'istanza.
Unauthorized	authz_fail	Fallimento autorizzazione (Status: failure, Reason: Forbidden).
JWT Usage	authn_token_use	Tracciamento utilizzo token JWT.
Eccezione Inaspettata	unexpected_exception	Errori non gestiti (EventLevel 2, Status: failure).
Validazione Dati	input_validation_fail	Errori in input (Status: failure, Reason: Exception Message).


--------------------------------------------------------------------------------


5. Configurazione Centralizzata e Hardening

SpringLine 2 integra Spring Cloud Config aggiungendo uno strato di resilienza tramite cache locale per nodo.

Resilienza e Cache

In caso di indisponibilità del Configuration Server, ogni nodo distribuito utilizza la propria cache locale autonoma, garantendo l'avvio dell'applicazione con l'ultima configurazione valida.

* spl.cloud.config.local-cache.enabled=true (Default: True)
* spring.cloud.config.enabled=true (Necessario per attivare il sistema)

Hardening Tomcat Embedded

Il framework applica configurazioni di sicurezza predefinite per il server embedded:

* server.max-http-request-header-size=2MB
* server.tomcat.connection-timeout=5s
* server.error.whitelabel.enabled=true
* server.error.include-stacktrace=never


--------------------------------------------------------------------------------


6. Sicurezza e Identità (Security)

Il modulo Security estende Spring Security per supportare gli Identity Provider (IdP) istituzionali.

Identity Provider Supportati

* IAM: SSO regionale basato su CA.
* API Manager: Identificazione tramite x-jwt-assertion.
* IDPC: Identità Digitale del Cittadino (CIE/SPID).
* SAMWEB: Operatori sanitari SISS.
* SpringLine: Interoperabilità con la versione 1.x.
* Propagator / Anonymous: Gestione accessi veicolati o non autenticati.

JWT e Authorities

Il JWT non è il meccanismo primario di auth, ma un mezzo di arricchimento dati. La classe JwtToken fornisce utility per generare token o cookie (generateToken, generateCookie).

Il controllo accessi è granulare e si basa su una nomenclatura standard delle Authorities:

* USERTYPE_, CIRCUIT_ (R=Reale, V=Virtuale), ROLE_, METHOD_, STRENGTH_ (0-100), CHANNEL_, DATASOURCE_, CLAIM_.

Annotazioni per lo Sviluppatore

Per l'accesso rapido ai dati utente nei Controller, utilizzare le annotazioni dedicate (es. @AuthUser, @AuthName, @AuthFiscalNumber, @AuthRole). È obbligatorio usare @Parameter(hidden = true) per evitare l'esposizione dei parametri in OpenAPI.


--------------------------------------------------------------------------------


7. Integrazione Dati e Persistenza (Data)

Il modulo estende JPA introducendo la distinzione tra DataSource Reali e Virtuali, critica per il settore sanitario.

Logica Reale/Virtuale

Il sistema seleziona il DataSource in base all'identità dell'utente fornita dall'IdP. Il monitoraggio del tempo di esecuzione query è abilitabile tramite spl.datasource.logging.execution-time.

Configurazione YAML (Esempio Hikari):

spl:
  datasource:
    reale-virtuale: true
    reale:
      url: jdbc:oracle:thin:@...
      username: app_user_r
      hikari:
        minimumIdle: 5
        maximumPoolSize: 20
    virtuale:
      url: jdbc:oracle:thin:@...
      username: app_user_v



--------------------------------------------------------------------------------


8. Comunicazione e Messaggistica

API Rest

Uniformità garantita da RestResponse e RestResponseBuilder. Gli errori sono gestiti dal GlobalExceptionHandler che automatizza la scrittura degli Audit Log per violazioni di sicurezza o validazione.

Web Services (WS)

Il modulo personalizza il WebServiceTemplate per abilitare il logging MON/MON-APP e la Propagazione SISS. Quest'ultima è specifica per i servizi sviluppati con tecnologia Handerground.

JMS (ActiveMQ)

Si utilizza SplJmsTemplate per l'invio e i Listener per la ricezione. A causa della natura asincrona, le transazioni JMS sono processate come utente anonimo, a meno di esplicita gestione del contesto.


--------------------------------------------------------------------------------


9. Processi Batch e OpenAPI

Batch

L'uso del ContextBatchListener è obbligatorio. Esso presiede al ciclo di vita del Context (creazione alla partenza del job, distruzione alla fine) e alla generazione automatica dei log di Audit relativi al job.

OpenAPI (Swagger)

Integrazione automatica agli endpoint /swagger-ui/index.html e /v3/api-docs. Per abilitare i test delle API protette direttamente da interfaccia, configurare:

* spl.open-api.info.title: Titolo API.
* spl.open-api.authorize.iam=true: Abilita auth IAM in Swagger.
* spl.open-api.authorize.idpc=true: Abilita auth IDPC in Swagger.


--------------------------------------------------------------------------------


10. Inizializzazione Progetti tramite Archetype Maven

Per avviare nuovi progetti secondo gli standard ARIA, utilizzare il comando Maven con il archetypeGroupId corretto:

mvn archetype:generate \
  -DarchetypeGroupId=it.ariaspa.springwiz \
  -DarchetypeArtifactId=springwiz.api-archetype \
  -DarchetypeVersion=2024.02.01 \
  -DcodiceApplicativoCodiceModuloElettra=lsccs \
  -DinteractiveMode=false


Nota: Il parametro codiceApplicativoCodiceModuloElettra deve essere sempre inserito in minuscolo.


--------------------------------------------------------------------------------


11. Appendice: Proprietà di Configurazione Default

Server & Hardening

* server.error.whitelabel.enabled=true
* server.max-http-request-header-size=2MB
* server.tomcat.connection-timeout=5s
* spl.error.exception-handler.enabled=true

Security & Authentication

* spl.security.generated-dev-password.enabled=false
* spl.security.authentication.iam.enabled=false
* spl.security.authentication.jwt.enabled=false
* spl.security.authentication.jwt.cookie.name=springline2Token
* spl.security.authentication.anonymous.uri-matchers=/favicon.ico

Logging (MON/MON-APP)

* spl.http.logging.mon.enabled=true
* spl.http.logging.app.enabled=false
* spl.http.logging.app.body-max-length=1048576
* spl.client-rest.logging.mon.enabled=true

DataSource

* spl.datasource.reale-virtuale=false
* spl.datasource.logging.execution-time=false

OpenAPI

* spl.open-api.configuration=true
* spl.open-api.authorize.iam=false
* spl.open-api.authorize.idpc=false
