# AGENTS.md — Regole Globali per gli Agenti OpenCode

Questo file definisce le regole e il contesto che tutti gli agenti OpenCode devono rispettare
quando lavorano su questo progetto.

---

## Progetto

**Nome**: `mypay.mypaycore`
**Tipo**: Middleware di integrazione SOAP ↔ OAuth2
**Framework**: SpringLine2 (ARIA S.p.A.) — estensione proprietaria di Spring Boot 3.x
**Linguaggio**: Java 17
**Build**: Maven multi-modulo
**Piattaforma di sviluppo**: Windows/WSL

### Cosa fa il progetto

Il middleware si interpone tra i **SIL** (Sistemi Informativi Locali) degli enti pubblici e la
**Piattaforma Unitaria** di pagoPA:
- Espone **40 operazioni SOAP** distribuite su **10 endpoint** ai SIL (9 MyPay + 1 MyPivot)
- Gestisce autonomamente l'**autenticazione OAuth2** verso pagoPA
- **Inoltra** le richieste autenticate e restituisce le risposte
- Supporta **routing dinamico** per ente (DB-driven) con cache duale (codIpa + codiceFiscale)

---

## Struttura del repository

```
mypay.mypaycore/                         ← root, parent POM
├── mypay.mypaycore-springboot/          ← applicazione Spring Boot
│   └── src/
│       ├── main/java/it/ariaspa/mypay/mypaycore/api/
│       │   ├── config/
│       │   ├── auth/
│       │   ├── client/
│       │   ├── soap/
│       │   │   ├── endpoint/
│       │   │   │   ├── AbstractSoapProxyEndpoint.java  ← classe base proxy SOAP
│       │   │   │   ├── mypay/                          ← 4 endpoint MyPay
│       │   │   │   ├── mypay/fesp/                     ← 5 endpoint MyPay FESP
│       │   │   │   └── mypivot/                        ← 1 endpoint MyPivot (Reconciliation)
│       │   │   └── exception/
│       │   ├── domain/
│       │   ├── repository/
│       │   ├── routing/
│       │   ├── logging/
│       │   ├── metrics/
│       │   ├── common/exception/
│       │   ├── health/
│       │   └── util/
│       └── test/java/...
├── mypay.mypaycore-properties/          ← application*.properties per il deploy
├── mypay.mypaycore-db/                  ← script SQL (DDL e DML)
├── docs/                                ← tutta la documentazione (in italiano)
│   ├── guidelines/DOCUMENTAZIONE_TECNICA.md      ← guida tecnica principale (SSoT)
│   ├── guidelines/Plan.md               ← stato fasi e piano attività
│   ├── guidelines/SOAP_ARCHITECTURE_MIGRATION_GUIDE_MYPAY.md
│   ├── procedures/GUIDA_TEST_POSTMAN_END_TO_END.md
│   └── springline2/RIASUNTO_SPRINGLINE2.md
├── .opencode/
│   ├── agents/                          ← agenti OpenCode
│   │   ├── expert.md                    ← @expert: esperto tecnico principale del progetto
│   │   ├── planner.md                   ← @planner: pianificazione e documentazione
│   │   ├── tester.md                    ← @tester: testing Java e collection Postman
│   │   ├── security.md                  ← @security: audit e analisi sicurezza applicativa
│   │   └── orchestrator.md              ← @orchestrator: gestione ecosistema AI
│   ├── commands/                        ← comandi personalizzati OpenCode
│   │   └── md-to-pdf.md                 ← comando per convertire Markdown in PDF
│   └── skills/                          ← skill OpenCode (conoscenza specializzata)
│       ├── caveman/
│       ├── diagnose/
│       ├── grill-me/
│       ├── grill-with-docs/
│       ├── improve-codebase-architecture/
│       ├── java-coding-standards/
│       ├── java-docs/
│       ├── setup-matt-pocock-skills/
│       ├── springline2/
│       ├── tdd/
│       ├── to-issues/
│       ├── to-prd/
│       ├── triage/
│       ├── write-a-skill/
│       └── zoom-out/
└── AGENTS.md                            ← questo file
```

---

## Regole obbligatorie per tutti gli agenti

### Codice

1. **Non introdurre dipendenze non approvate** senza prima verificare la compatibilità con
   il parent POM corporate `it.ariaspa:cm:1.0.0`
2. **Rispettare il prefisso datasource** `spring.datasource.pa.*` (non `spring.datasource.*`)
3. **Non creare il profilo `local`** — è stato rimosso intenzionalmente; l'unico profilo attivo è `dev`
4. **Non usare `WsConfigurerAdapter`** (deprecato) — usare l'interfaccia `WsConfigurer`
5. **Sicurezza XXE**: qualsiasi parsing XML deve usare `DocumentBuilderFactory` con DTD e
   external entities disabilitati
6. **Non loggare dati sensibili**: token OAuth2, `client_secret`, password SOAP e dati
   personali non devono MAI apparire nei log, a nessun livello. Per verifiche di sicurezza
   approfondite, invocare l'agente **@security**

### Compilazione (Windows/WSL)

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn compile -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

Aggiungere sempre `-Denforcer.skip=true` perché il parent POM enforcer richiede OS Unix.

### Test

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-17&& mvn test -pl mypay.mypaycore-springboot -am -Denforcer.skip=true"
```

### Documentazione

- Tutta la documentazione va scritta in **italiano**
- Dopo ogni modifica significativa al codice, aggiornare:
  - `docs/guidelines/DOCUMENTAZIONE_TECNICA.md` (incrementare versione e data)
  - `docs/guidelines/Plan.md` (aggiornare stato fasi)
- Per pianificazione e aggiornamento docs, invocare l'agente **@planner**

### Conversione Markdown in PDF

- Il progetto dispone del comando personalizzato `/md-to-pdf` per convertire file Markdown in PDF
- Il comando usa la libreria globale npm `md-to-pdf` (già installata nell'ambiente)
- Utilizzo: `/md-to-pdf path/to/file.md` (se non specificato, converte DOCUMENTAZIONE_TECNICA.md)
- Il PDF generato usa formattazione professionale (formato A4, margini ottimizzati)
- Il file PDF viene creato nella stessa directory del file Markdown sorgente

### Commenti e documentazione nel codice

7. **Documentare sempre il codice** — ogni classe, metodo e blocco logico non ovvio deve essere
   documentato per facilitare la comprensione degli sviluppatori che lavorano sul progetto.
   Seguire queste linee guida:

   - **Classi**: Javadoc obbligatorio su ogni classe pubblica. Descrivere lo scopo, il contesto
     d'uso e le dipendenze principali.
   - **Metodi pubblici e package-private**: Javadoc obbligatorio. Includere `@param`, `@return`
     e `@throws` dove applicabile.
   - **Metodi privati complessi**: commento inline `//` se la logica non è immediatamente chiara.
   - **Costanti e campi**: commento Javadoc `/** ... */` se il significato non è autoesplicativo.
   - **Blocchi logici non ovvi**: commento inline che spiega il *perché* (non solo il *cosa*).
   - **TODO / decisioni future**: usare il tag `// TODO (IT): ...` in italiano.

8. **Lingua dei commenti e Javadoc**: tutto il testo di commenti, Javadoc, tag descrittivi e
   messaggi di log significativi deve essere scritto in **italiano**. Nessuna eccezione.

   Esempi corretti:
   ```java
   /**
    * Gestisce l'autenticazione OAuth2 verso la Piattaforma Unitaria.
    * Il token viene mantenuto in cache e rinnovato automaticamente alla scadenza.
    */
   public class OAuthTokenService { ... }

   /**
    * Restituisce un token OAuth2 valido, recuperandolo dalla cache se disponibile
    * o richiedendone uno nuovo alla Piattaforma Unitaria.
    *
    * @return token Bearer valido
    * @throws PiattaformaAuthenticationException se l'autenticazione fallisce
    */
   public String getValidToken() { ... }

   // Verifica se il token è scaduto con un margine di 30 secondi per evitare race condition
   if (tokenExpiresAt.isBefore(Instant.now().plusSeconds(30))) { ... }
   ```

   Esempi errati (da evitare):
   ```java
   /** Gets a valid OAuth2 token */           // ❌ inglese
   // check if expired                         // ❌ inglese
   // TODO: implement retry logic              // ❌ inglese
   ```

---

## Agenti disponibili

| Agente | Quando usarlo |
|--------|---------------|
| `@expert` | Decisioni architetturali, code review, debugging complesso, guida tecnica esperta sul middleware SOAP ↔ OAuth2, valutazione rischi e debito tecnico |
| `@planner` | Pianificare nuove fasi, aggiornare docs/, allineare Plan.md dopo modifiche |
| `@tester` | Scrivere test unitari Java (JUnit 5 + Mockito), test di integrazione (@SpringBootTest, WireMock), gestire la collection Postman, aggiornare la guida test E2E |
| `@security` | Audit di sicurezza del codice (XXE, injection, credenziali), verifica flusso OAuth2, analisi log per dati sensibili, security code review, analisi CVE dipendenze |
| `@orchestrator` | Gestire ecosistema AI: creare/auditare/ottimizzare agenti, skill, comandi e MCP server |

## Skill disponibili

| Skill | File | Quando caricarla |
|-------|------|-----------------|
| `caveman` | `.opencode/skills/caveman/SKILL.md` | Comunicazione ultra-compressa (~75% token in meno). Quando l'utente dice "caveman mode", "talk like caveman", "less tokens", o invoca `/caveman` |
| `diagnose` | `.opencode/skills/diagnose/SKILL.md` | Diagnostica strutturata di bug complessi e regressioni di performance. Quando l'utente dice "diagnose this", "debug this", segnala un bug o un degrado di performance |
| `grill-me` | `.opencode/skills/grill-me/SKILL.md` | Intervista approfondita su un piano o design fino a raggiungere comprensione condivisa. Quando l'utente vuole stress-testare un piano o dice "grill me" |
| `grill-with-docs` | `.opencode/skills/grill-with-docs/SKILL.md` | Sessione di grilling che verifica il piano contro il modello di dominio esistente e aggiorna la documentazione (CONTEXT.md, ADR) |
| `improve-codebase-architecture` | `.opencode/skills/improve-codebase-architecture/SKILL.md` | Identifica opportunità di miglioramento architetturale, refactoring e moduli più testabili. Quando l'utente vuole migliorare l'architettura o rendere il codice più testabile |
| `java-coding-standards` | `.opencode/skills/java-coding-standards/SKILL.md` | Standard di codifica Java per servizi Spring Boot: naming, immutabilità, Optional, stream, eccezioni, generici, layout progetto |
| `java-docs` | `.opencode/skills/java-docs/SKILL.md` | Documentazione Javadoc per tipi Java: best practice per commenti, `@param`, `@return`, `@throws`, `{@code}` |
| `setup-matt-pocock-skills` | `.opencode/skills/setup-matt-pocock-skills/SKILL.md` | Configura il blocco `## Agent skills` in AGENTS.md e `docs/agents/`. Da eseguire prima di `to-issues`, `to-prd`, `triage`, `diagnose`, `tdd`, `improve-codebase-architecture` o `zoom-out` |
| `springline2` | `.opencode/skills/springline2/SKILL.md` | Ogni volta che si lavora su configurazione, sicurezza, logging, client SOAP/REST o dipendenze SpringLine2 |
| `tdd` | `.opencode/skills/tdd/SKILL.md` | Test-Driven Development con ciclo red-green-refactor. Quando l'utente vuole sviluppare con TDD o menziona "red-green-refactor" |
| `to-issues` | `.opencode/skills/to-issues/SKILL.md` | Suddivide un piano/spec/PRD in issue indipendenti sull'issue tracker usando vertical slice (tracer bullet) |
| `to-prd` | `.opencode/skills/to-prd/SKILL.md` | Converte il contesto della conversazione in un PRD e lo pubblica sull'issue tracker |
| `triage` | `.opencode/skills/triage/SKILL.md` | Gestisce il flusso di triage delle issue attraverso una state machine. Quando l'utente vuole creare, valutare o gestire issue |
| `write-a-skill` | `.opencode/skills/write-a-skill/SKILL.md` | Crea nuove skill con struttura, progressive disclosure e risorse. Quando l'utente vuole creare o scrivere una nuova skill |
| `zoom-out` | `.opencode/skills/zoom-out/SKILL.md` | Fornisce contesto più ampio e prospettiva di alto livello su una sezione di codice. Quando non si conosce bene un'area del codice |

---

## Profili applicativi

Attualmente è attivo **un solo profilo** per semplificare lo sviluppo e l'onboarding.
I profili `uat` e `prod` verranno creati in futuro quando necessario.

| Profilo | Scopo | Logging | Resilienza | Stato |
|---------|-------|---------|-----------|-------|
| `dev` | Sviluppo locale | DEBUG | Rilassata | **Attivo** |
| `uat` | Test integrazione | INFO | Standard | Da creare |
| `prod` | Produzione | WARN | Conservativa | Da creare |

---

## Vincoli noti

| Vincolo | Dettaglio |
|---------|-----------|
| Enforcer Maven | Richiede OS Unix — usare `-Denforcer.skip=true` su Windows |
| DataSource | Prefisso `spring.datasource.pa.*`, configurato manualmente in `DataSourceConfiguration.java` con `JdbiConfiguration.java`. Repository basati su Jdbi (non JPA) |
| Spring WS | Libreria `springline2-ws` è client SOAP, non server — server gestito da `spring-boot-starter-web-services` |
| Profilo `local` | Rimosso — non ricreare |
