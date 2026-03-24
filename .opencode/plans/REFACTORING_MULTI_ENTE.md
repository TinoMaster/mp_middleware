# Refactoring Multi-Ente OAuth2 — Piano di Implementazione

**Versione**: 1.0
**Data creazione**: 24/03/2026
**Stato**: IN CORSO

---

## Obiettivo

Trasformare il middleware da autenticazione OAuth2 globale (un solo token per tutti gli enti) ad
autenticazione **per-ente** (ogni ente ha le proprie credenziali `client_id`/`client_secret`),
eliminando la tabella `mwpay_ente_config` e usando direttamente le tabelle condivise
`mygov_ente` + nuova `mygov_ente_config_pu`.

---

## Decisioni Architetturali Confermate

| Decisione | Risultato |
|-----------|-----------|
| Tabella `mwpay_ente_config` | **Eliminata** — routing derivato da `mygov_ente_config_pu` |
| Concetto `tipoOperazione` | **Rimosso** — routing e per-ente, non per-operazione |
| FK nuova tabella | `codice_ipa_ente` -> `mygov_ente.cod_ipa_ente`, relazione 1:1 |
| Campi extra `mygov_ente_config_pu` | `attivo` (boolean), `dt_creazione`, `dt_ultima_modifica` |
| Parametri OAuth2 globali | `token_url`, `base_url`, `scope`, `grant_type` restano in properties; solo `client_id`/`client_secret` dal DB per-ente |
| Cache token | `ConcurrentHashMap<codIpaEnte, TokenData>` in memoria |
| Storage `client_secret` | Testo in chiaro (per ora) |
| Ente non in `mygov_ente` | SOAP Fault `ENTE_NON_AUTORIZZATO` |
| Config PU con `attivo=false` | Fallback a legacy |
| Validazione password | NON validare — solo verifica esistenza `codIpaEnte` |
| Validazione `cd_stato_ente` | NON richiesta |
| DB | Stesso DB condiviso con mypay/mypivot (`spring.datasource.pa.*`) |
| Test Java | **TUTTI ELIMINATI** — solo test Postman E2E |
| Dipendenze test | **RIMOSSE** dal POM |

---

## Nuovo Flusso Atteso

```
SIL -> POST /ws/pivot/...
  -> ReconciliationEndpoint
    -> extract codIpaEnte da SOAP Header
    -> EnteCacheService.findByCodIpaEnte(codIpaEnte)
      -> query: mygov_ente LEFT JOIN mygov_ente_config_pu
      -> non trovato? -> EnteNonCensitoException -> SOAP Fault
    -> PathRegistryConfig.resolveBackend(path) -> MYPAY/MYPIVOT
    -> EnteCompleto.isPiattaformaUnitaria()?
      -> SI: PiattaformaUnitariaClient.forwardSoapRequest(path, xml, codIpaEnte)
        -> OAuthTokenService.getAccessToken(codIpaEnte) [cache per-ente]
      -> NO: ProxyForwardingClient.forwardToLegacyBackend(dest, path, xml)
```

---

## Fasi di Implementazione

### Fase A: Script SQL
- [ ] `004_CREATE_MYGOV_ENTE_CONFIG_PU.sql` — DDL nuova tabella
- [ ] `005_DROP_MWPAY_ENTE_CONFIG.sql` — drop vecchia tabella
- [ ] `006_INSERT_ENTE_CONFIG_PU_EXAMPLE.sql` — dati esempio
- [ ] Eliminare `001_CREATE_MWPAY_ENTE_CONFIG.sql`
- [ ] Eliminare `003_INSERT_ENTE_CONFIG_EXAMPLE.sql`

### Fase B: Domain
- [ ] Creare `Ente.java` — dominio per `mygov_ente`
- [ ] Creare `EnteConfigPu.java` — dominio per `mygov_ente_config_pu`
- [ ] Creare `EnteCompleto.java` — DTO composito (ente + config PU opzionale)
- [ ] Eliminare `EnteConfig.java`
- [ ] Valutare/semplificare `ModalitaRouting.java`

### Fase C: Repository
- [ ] Creare `EnteRepository.java` — JDBI DAO
- [ ] Creare `EnteConfigPuRepository.java` — JDBI DAO
- [ ] Creare `EnteCompletoRowMapper.java`
- [ ] Eliminare `EnteConfigRepository.java`
- [ ] Eliminare `EnteConfigRowMapper.java`
- [ ] Aggiornare `JdbiConfiguration.java` con nuovi DAO

### Fase D: Cache
- [ ] Refactoring `EnteConfigCacheService` -> `EnteCacheService`
- [ ] Chiave cache: solo `codIpaEnte` (no piu `tipoOperazione`)
- [ ] Valore: `EnteCompleto`
- [ ] Metodo principale: `findByCodIpaEnte(codIpaEnte)` -> `Optional<EnteCompleto>`

### Fase E: Auth
- [ ] Refactoring `OAuthTokenService` per multi-ente
  - `ConcurrentHashMap<String, TokenData>` con chiave `codIpaEnte`
  - Metodo: `getAccessToken(codIpaEnte)` -> prende `client_id`/`client_secret` dal cache ente
  - Inner class `TokenData` con `token`, `expiryTime`
- [ ] Eliminare `OAuthTokenInterceptor.java`
- [ ] Aggiornare `PiattaformaUnitariaConfig.java` — rimuovere `clientId`/`clientSecret` dalla inner class `Auth`

### Fase F: Routing
- [ ] Semplificare `RoutingDecisionService`:
  - Firma: `decide(codIpaEnte, pathRichiesta)` (NO piu tipoOperazione)
  - Usa `EnteCacheService` anziche `EnteConfigCacheService`
  - Routing derivato da `EnteCompleto.isPiattaformaUnitaria()`
- [ ] Aggiornare `RoutingDecision`:
  - Includere `EnteCompleto` nel risultato
  - Rimuovere dipendenza da `ModalitaRouting` enum (o semplificarlo)

### Fase G: Client
- [ ] Aggiornare `PiattaformaUnitariaClient`:
  - Firma: `forwardSoapRequest(path, soapXml, codIpaEnte)`
  - Rimuovere dipendenza da `OAuthTokenInterceptor`
  - Aggiungere Bearer manualmente: `oAuthTokenService.getAccessToken(codIpaEnte)`
  - Aggiornare fallback con `codIpaEnte`
  - Aggiornare retry con `codIpaEnte`

### Fase H: Endpoint
- [ ] Aggiornare `ReconciliationEndpoint`:
  - Rimuovere estrazione `tipoOperazione` dal body SOAP
  - Chiamare `routingDecisionService.decide(codIpaEnte, requestPath)` (2 param)
  - Passare `codIpaEnte` a `piattaformaClient.forwardSoapRequest(path, xml, codIpaEnte)`
  - Aggiornare logging/metriche

### Fase I: Eccezioni
- [ ] Aggiornare `EnteNonCensitoException` — rimuovere `tipoOperazione`
- [ ] Verificare `SoapFaultExceptionResolver` gestisca correttamente i nuovi casi
- [ ] Verificare altre eccezioni se necessario

### Fase J: Health/Metriche/Logging
- [ ] `OAuthTokenHealthIndicator` — multi-ente (iterare sulla cache token)
- [ ] `EnteConfigHealthIndicator` — nuova logica con `EnteCacheService`
- [ ] `TransactionLoggingService` — aggiornare firma/log (rimuovere tipoOperazione dove necessario)
- [ ] `MiddlewareMetricsService` — aggiornare firma/metriche

### Fase K: Eliminazione Test
- [ ] Eliminare intera directory `src/test/`
- [ ] Rimuovere da POM: `spring-boot-starter-test`, `spring-ws-test`
- [ ] Aggiornare collection Postman con nuova struttura

### Fase L: Properties
- [ ] `application.properties` (base) — rimuovere `client-id`, `client-secret`; aggiornare commento cache
- [ ] `application-dev.properties` — rimuovere `client-id`, `client-secret`; aggiornare commenti

### Fase M: Documentazione
- [ ] Aggiornare `docs/guidelines/DOCUMENTAZIONE_PRIMA_FASE.md`
- [ ] Aggiornare `docs/guidelines/Plan.md`
- [ ] Aggiornare `AGENTS.md`
- [ ] Aggiornare `.opencode/agents/tester.md` — solo Postman
- [ ] Aggiornare `docs/procedures/GUIDA_TEST_POSTMAN_END_TO_END.md`

---

## File Coinvolti

### Da Creare
| File | Descrizione |
|------|-------------|
| `mypay.mypaycore-db/src/main/sql/004_CREATE_MYGOV_ENTE_CONFIG_PU.sql` | DDL nuova tabella |
| `mypay.mypaycore-db/src/main/sql/005_DROP_MWPAY_ENTE_CONFIG.sql` | Drop vecchia tabella |
| `mypay.mypaycore-db/src/main/sql/006_INSERT_ENTE_CONFIG_PU_EXAMPLE.sql` | Dati esempio |
| `.../api/domain/Ente.java` | Dominio mygov_ente |
| `.../api/domain/EnteConfigPu.java` | Dominio mygov_ente_config_pu |
| `.../api/domain/EnteCompleto.java` | DTO composito |
| `.../api/repository/EnteRepository.java` | DAO JDBI |
| `.../api/repository/EnteConfigPuRepository.java` | DAO JDBI |
| `.../api/repository/EnteCompletoRowMapper.java` | Row mapper JOIN |

### Da Eliminare
| File | Motivo |
|------|--------|
| `.../api/domain/EnteConfig.java` | Sostituito da Ente + EnteConfigPu + EnteCompleto |
| `.../api/repository/EnteConfigRepository.java` | Sostituito da EnteRepository + EnteConfigPuRepository |
| `.../api/repository/EnteConfigRowMapper.java` | Sostituito da EnteCompletoRowMapper |
| `.../api/auth/OAuthTokenInterceptor.java` | Bearer aggiunto manualmente in PiattaformaUnitariaClient |
| `mypay.mypaycore-db/src/main/sql/001_CREATE_MWPAY_ENTE_CONFIG.sql` | Tabella eliminata |
| `mypay.mypaycore-db/src/main/sql/003_INSERT_ENTE_CONFIG_EXAMPLE.sql` | Tabella eliminata |
| Intera directory `src/test/` | Test solo via Postman |

### Da Modificare
| File | Tipo di Modifica |
|------|-----------------|
| `OAuthTokenService.java` | Multi-ente ConcurrentHashMap token |
| `PiattaformaUnitariaConfig.java` | Rimuovere clientId/clientSecret |
| `PiattaformaUnitariaClient.java` | Ricevere codIpaEnte, Bearer manuale |
| `EnteConfigCacheService.java` -> `EnteCacheService.java` | Rinominare + nuova logica |
| `RoutingDecisionService.java` | Semplificato, no tipoOperazione |
| `RoutingDecision.java` | Include EnteCompleto |
| `ReconciliationEndpoint.java` | Nuovo flusso |
| `SoapFaultExceptionResolver.java` | Nuovi casi |
| `JdbiConfiguration.java` | Nuovi DAO |
| `OAuthTokenHealthIndicator.java` | Multi-ente |
| `EnteConfigHealthIndicator.java` | Nuova logica |
| `TransactionLoggingService.java` | Aggiornato |
| `MiddlewareMetricsService.java` | Aggiornato |
| `EnteNonCensitoException.java` | Rimuovere tipoOperazione |
| `application.properties` | Rimuovere credenziali globali |
| `application-dev.properties` | Rimuovere credenziali globali |
| `pom.xml` (springboot) | Rimuovere dipendenze test |

---

## Stato Avanzamento

| Fase | Stato | Note |
|------|-------|------|
| A | `DA FARE` | |
| B | `DA FARE` | |
| C | `DA FARE` | |
| D | `DA FARE` | |
| E | `DA FARE` | |
| F | `DA FARE` | |
| G | `DA FARE` | |
| H | `DA FARE` | |
| I | `DA FARE` | |
| J | `DA FARE` | |
| K | `DA FARE` | |
| L | `DA FARE` | |
| M | `DA FARE` | |
| Compilazione | `DA FARE` | |

---

## Proprieta File

### application.properties (base: mypay.mypaycore-springboot/src/main/resources/config/)
- Righe 9-10: `piattaforma-unitaria.auth.client-id` e `client-secret` -> DA RIMUOVERE
- Riga 30-32: commento cache da aggiornare (`mwpay_ente_config` -> `mygov_ente + mygov_ente_config_pu`)

### application-dev.properties (stessa directory)
- Righe 39-40: `piattaforma-unitaria.auth.client-id` e `client-secret` -> DA RIMUOVERE
- Commenti: aggiornare riferimenti a credenziali globali

### application.properties (deploy: mypay.mypaycore-properties/src/main/resources/)
- Nessuna modifica necessaria (non contiene credenziali PU)
