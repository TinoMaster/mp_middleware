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
- Espone endpoint **SOAP** ai SIL (protocollo legacy)
- Gestisce autonomamente l'**autenticazione OAuth2** verso pagoPA
- **Inoltra** le richieste autenticate e restituisce le risposte

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
│       │   ├── soap/endpoint/
│       │   ├── common/exception/
│       │   └── health/
│       └── test/java/...
├── mypay.mypaycore-properties/          ← application*.properties per il deploy
├── mypay.mypaycore-db/                  ← script SQL
├── docs/                                ← tutta la documentazione (in italiano)
│   ├── architettura/ARCHITETTURA_MIDDLEWARE.md
│   ├── guidelines/DOCUMENTAZIONE_PRIMA_FASE.md   ← guida tecnica principale
│   ├── guidelines/Plan.md               ← stato fasi e piano attività
│   ├── procedures/GUIDA_TEST_POSTMAN_END_TO_END.md
│   └── springline2/RIASUNTO_SPRINGLINE2.md
├── .opencode/
│   ├── agents/                          ← agenti OpenCode
│   │   ├── planner.md                   ← @planner: pianificazione e documentazione
│   │   └── orchestrator.md              ← @orchestrator: gestione ecosistema AI
│   └── skills/                          ← skill OpenCode (conoscenza specializzata)
│       └── springline2/
│           └── SKILL.md                 ← conoscenza completa framework SpringLine2
└── AGENTS.md                            ← questo file
```

---

## Regole obbligatorie per tutti gli agenti

### Codice

1. **Non introdurre dipendenze non approvate** senza prima verificare la compatibilità con
   il parent POM corporate `it.ariaspa:cm:1.0.0`
2. **Rispettare il prefisso datasource** `spring.datasource.pa.*` (non `spring.datasource.*`)
3. **Non creare il profilo `local`** — è stato rimosso intenzionalmente; l'unico profilo attivo è `dev`
4. **Non rigenerare `shutdown.pid`** — rimosso intenzionalmente
5. **Non usare `WsConfigurerAdapter`** (deprecato) — usare l'interfaccia `WsConfigurer`
6. **Sicurezza XXE**: qualsiasi parsing XML deve usare `DocumentBuilderFactory` con DTD e
   external entities disabilitati

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
  - `docs/guidelines/DOCUMENTAZIONE_PRIMA_FASE.md` (incrementare versione e data)
  - `docs/guidelines/Plan.md` (aggiornare stato fasi)
- Per pianificazione e aggiornamento docs, invocare l'agente **@planner**

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
| `@planner` | Pianificare nuove fasi, aggiornare docs/, allineare Plan.md dopo modifiche |
| `@orchestrator` | Gestire ecosistema AI: creare/auditare/ottimizzare agenti, skill, comandi e MCP server |

## Skill disponibili

| Skill | File | Quando caricarla |
|-------|------|-----------------|
| `springline2` | `.opencode/skills/springline2/SKILL.md` | Ogni volta che si lavora su configurazione, sicurezza, logging, client SOAP/REST o dipendenze SpringLine2 |

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
| DataSource | Prefisso `spring.datasource.pa.*`, configurato manualmente in `DataSourceConfig.java` |
| Spring WS | Libreria `springline2-ws` è client SOAP, non server — server gestito da `spring-boot-starter-web-services` |
| Profilo `local` | Rimosso — non ricreare |
| `shutdown.pid` | Rimosso — non ricreare logica `ApplicationPidFileWriter` |
