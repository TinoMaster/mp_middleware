# Panoramica del Progetto mypay.mypaycore

## Middleware di Integrazione SOAP — OAuth2 per la Piattaforma Unitaria pagoPA

**Versione documento**: 1.0.0
**Data**: 23 Marzo 2026
**Destinatari**: Responsabili di progetto e stakeholder

---

## Indice

1. [Introduzione e contesto](#1-introduzione-e-contesto)
2. [Architettura del sistema](#2-architettura-del-sistema)
3. [Modello di routing](#3-modello-di-routing)
4. [Componenti principali](#4-componenti-principali)
5. [Sicurezza](#5-sicurezza)
6. [Resilienza e monitoraggio](#6-resilienza-e-monitoraggio)
7. [Database](#7-database)
8. [Stato attuale e prossimi passi](#8-stato-attuale-e-prossimi-passi)
9. [Glossario](#9-glossario)

---

## 1. Introduzione e contesto

### Il problema

Gli enti pubblici (comuni, province, ecc.) dispongono di **Sistemi Informativi Locali (SIL)** che comunicano con i sistemi regionali tramite protocollo **SOAP**. La nuova **Piattaforma Unitaria (PU)** di pagoPA richiede autenticazione **OAuth2**, un protocollo che i SIL non sono in grado di gestire autonomamente.

### La soluzione

`mypay.mypaycore` è un **middleware** che si interpone tra i SIL e i sistemi di backend (mypay, mypivot) e la Piattaforma Unitaria. In sintesi:

- **Riceve** richieste SOAP dai SIL, senza richiedere alcuna modifica ai sistemi degli enti
- **Gestisce in autonomia** l'autenticazione OAuth2 verso pagoPA
- **Instrada** ogni richiesta verso la destinazione corretta (Piattaforma Unitaria o backend legacy)
- **Restituisce** la risposta al SIL nel formato atteso

I SIL continuano a utilizzare gli stessi path e lo stesso protocollo SOAP di sempre. Il middleware è completamente trasparente per loro.

---

## 2. Architettura del sistema

### Flusso generale

```
Ente Pubblico (SIL)
       │
       │  Richiesta SOAP
       │  (stessi path e formato di sempre)
       ▼
┌──────────────────────────────────────────────────┐
│           MIDDLEWARE  mypay.mypaycore             │
│                                                  │
│  1. Riceve la richiesta SOAP                     │
│  2. Identifica l'ente (codIpaEnte nell'Header)   │
│  3. Decide la destinazione e la modalità         │
│     consultando il database                      │
│  4a. Piattaforma Unitaria: aggiunge token OAuth2 │
│  4b. Legacy: forward diretto (nessuna modifica)  │
│  5. Restituisce la risposta al SIL               │
│  6. Registra la transazione (log + metriche)     │
└──────────────────────────────────────────────────┘
       │                           │
       │  Modalità PU              │  Modalità LEGACY
       │  (con token OAuth2)       │  (forward diretto)
       ▼                           ▼
 Piattaforma Unitaria        Backend Legacy
     (pagoPA)              (mypay o mypivot)
```

### Framework e tecnologie

| Tecnologia | Versione | Ruolo |
|-----------|---------|-------|
| **SpringLine2** (ARIA S.p.A.) | 2027.01.01 | Framework base — estensione proprietaria di Spring Boot |
| **Spring Boot** | 3.5.5 | Piattaforma applicativa (incluso nel framework SpringLine2) |
| **Spring Web Services** | (gestita da Spring Boot) | Esposizione endpoint SOAP server-side |
| **Resilience4j** | 2.2.0 | Circuit breaker e retry automatico |
| **Jdbi 3** | 3.27.0 | Accesso al database PostgreSQL |
| **PostgreSQL** | (gestita da Spring Boot) | Database relazionale |
| **Micrometer** | (gestita da Spring Boot) | Metriche operative |
| **Java** | 17 | Linguaggio |

### Struttura dei moduli

Il progetto è organizzato in moduli Maven indipendenti:

| Modulo | Contenuto |
|--------|-----------|
| `mypay.mypaycore-springboot` | Codice sorgente Java dell'applicazione (cuore del middleware) |
| `mypay.mypaycore-db` | Script SQL PostgreSQL per la creazione delle tabelle |
| `mypay.mypaycore-properties` | File di configurazione e template per il deployment |
| `mypay.mypaycore-release` | Packaging per il rilascio |

---

## 3. Modello di routing

Il middleware implementa un **routing a due dimensioni** per ogni richiesta ricevuta:

### Dimensione 1 — Destinazione (per path HTTP)

Il path della richiesta SOAP determina il **backend di destinazione**:

| Path richiesta | Backend destinazione |
|---------------|---------------------|
| `/ws/pivot/*` | **mypivot** |
| `/ws/pa/*` | **mypay** |
| `/ws/fesp/*` | **mypay** |

Questo mapping è **configurabile** in `application.properties` senza modificare il codice. Se in futuro si aggiungono nuovi path, basta aggiungere una riga di configurazione.

### Dimensione 2 — Modalità (per ente e operazione, dal database)

Per ogni combinazione di ente (`codIpaEnte`) e tipo di operazione, il database indica la **modalità di instradamento**:

| Modalità | Comportamento |
|-----------|---------------|
| **PIATTAFORMA_UNITARIA** | Il middleware acquisisce un token OAuth2 e inoltra la richiesta alla PU con l'header `Authorization: Bearer` |
| **LEGACY** | Il middleware inoltra la richiesta direttamente al backend legacy senza modifiche |

Questo consente una **migrazione graduale** degli enti: è sufficiente aggiornare un record nel database per spostare un ente dalla modalità legacy alla Piattaforma Unitaria, senza toccare il codice né riavviare l'applicazione.

### Esempio concreto

```
Ente "SELC_99999000013" invia richiesta a /ws/pivot/...
  │
  ├── Dimensione 1: /ws/pivot/* → destinazione MYPIVOT
  │
  ├── Dimensione 2: DB dice PIATTAFORMA_UNITARIA per questo ente
  │
  = Il middleware inoltra a PU con token OAuth2
```

---

## 4. Componenti principali

L'applicazione è organizzata in moduli funzionali ben separati:

| Modulo | Responsabilità | Descrizione |
|--------|----------------|-------------|
| **Autenticazione** (`auth/`) | OAuth2 verso pagoPA | Gestisce il ciclo di vita del token (richiesta, cache, rinnovo automatico, thread-safety) |
| **Routing** (`routing/`) | Decisione di instradamento | Determina dove e come instradare ogni richiesta, combinando path HTTP e configurazione DB |
| **Client HTTP** (`client/`) | Comunicazione verso i backend | Due client: uno per la PU (con OAuth2) e uno per i backend legacy (forward diretto) |
| **Endpoint SOAP** (`soap/`) | Interfaccia verso i SIL | Riceve le richieste SOAP, orchestra il flusso, gestisce gli errori come SOAP Fault |
| **Persistenza** (`domain/`, `repository/`) | Accesso ai dati | Modelli di dominio, DAO Jdbi e cache in-memory con TTL configurabile |
| **Log transazionale** (`logging/`) | Tracciabilità | Ogni transazione viene registrata nel database con esito, durata e dettagli |
| **Metriche** (`metrics/`) | Osservabilità operativa | Contatori, timer e gauge esposti tramite Spring Actuator |
| **Health check** (`health/`) | Monitoraggio stato sistema | Verifica raggiungibilità PU, validità token OAuth2, presenza enti configurati |
| **Gestione errori** (`common/exception/`, `soap/exception/`) | Errori strutturati | Eccezioni tipizzate convertite in SOAP Fault con codici specifici |
| **Configurazione** (`config/`) | Setup applicativo | DataSource, Jdbi, SOAP servlet, mapping path/backend, parametri OAuth2 |

---

## 5. Sicurezza

### Autenticazione OAuth2 (machine-to-machine)

Il middleware utilizza il flusso **OAuth2 Client Credentials** per autenticarsi verso la Piattaforma Unitaria:

- Le credenziali (`client_id`, `client_secret`) vengono iniettate tramite **variabili d'ambiente**, mai hardcoded nel codice
- Il token viene mantenuto in **cache in-memory** e rinnovato automaticamente prima della scadenza (margine di 60 secondi)
- L'accesso alla cache è **thread-safe** (ReentrantLock + variabili volatile)
- In caso di risposta 401 dalla PU, il token viene rinnovato e la richiesta ritentata automaticamente

### Protezione XML (prevenzione attacchi XXE)

Tutte le operazioni di parsing XML sono protette contro gli attacchi **XXE** (XML External Entity):
- Dichiarazioni DTD bloccate
- Entità esterne disabilitate
- XInclude disabilitato

### Autenticazione dei SIL

I SIL si autenticano tramite `codIpaEnte` (nell'Header SOAP) e `password` (nel Body SOAP). Il middleware **non** richiede token JWT o Bearer ai SIL.

---

## 6. Resilienza e monitoraggio

### Resilienza

Il middleware implementa due pattern di resilienza tramite la libreria **Resilience4j**:

| Pattern | Comportamento |
|---------|---------------|
| **Circuit Breaker** | Se un backend non risponde ripetutamente (>50% errori su 10 chiamate), il circuito si "apre" e le richieste vengono bloccate per 30 secondi, evitando cascate di errori. Dopo il periodo di attesa, vengono permesse 3 chiamate di test. |
| **Retry con backoff esponenziale** | Le chiamate fallite vengono ritentate fino a 3 volte con attese crescenti (1s, 2s, 4s), per gestire errori transitori di rete. |

Questi pattern sono applicati sia al client verso la Piattaforma Unitaria che al client verso i backend legacy, con configurazioni indipendenti.

### Monitoraggio (Spring Actuator)

Il middleware espone endpoint di monitoraggio accessibili via HTTP:

| Endpoint | Informazione |
|----------|-------------|
| `/actuator/health` | Stato complessivo: connessione DB, raggiungibilità PU, validità token, enti configurati |
| `/actuator/metrics` | Metriche operative: richieste totali (per ente, operazione, modalità, esito), durata, enti configurati |
| `/actuator/circuitbreakers` | Stato corrente dei circuit breaker (aperto/chiuso) |

### Gestione errori verso i SIL

Ogni errore viene convertito in un **SOAP Fault** strutturato con codice specifico:

| Situazione | Tipo Fault | Codice |
|-----------|-----------|--------|
| Ente non censito nel DB | Client (errore del chiamante) | `ENTE_NON_AUTORIZZATO` |
| Path non riconosciuto | Client (errore del chiamante) | `PATH_NON_RICONOSCIUTO` |
| Errore di autenticazione OAuth2 | Server (errore del middleware) | `AUTH_ERROR` |
| Errore di comunicazione con backend | Server (errore del middleware) | `COMM_ERROR` |
| Errore interno generico | Server (errore del middleware) | `INTERNAL_ERROR` |

---

## 7. Database

Il middleware utilizza un database **PostgreSQL** con due tabelle dedicate (prefisso `mwpay_`):

### Tabella `mwpay_ente_config` — Configurazione routing

Contiene le regole di instradamento per ogni ente e tipo di operazione.

| Colonna | Descrizione |
|---------|-------------|
| `cod_ipa_ente` | Codice IPA dell'ente pubblico |
| `tipo_operazione` | Tipo di operazione SOAP (es. `pivotSILAutorizzaImportFlussoTesoreria`) |
| `modalita_routing` | `PIATTAFORMA_UNITARIA` oppure `LEGACY` |
| `attivo` | Flag per abilitare/disabilitare una regola senza eliminarla |

La coppia `(cod_ipa_ente, tipo_operazione)` è vincolata come chiave unica.

**Importante**: Per migrare un ente dalla modalità legacy alla Piattaforma Unitaria è sufficiente aggiornare il campo `modalita_routing` nel database. Non è necessario modificare il codice né riavviare il middleware.

### Tabella `mwpay_transaction_log` — Log transazionale

Registra ogni transazione SOAP processata dal middleware.

| Colonna | Descrizione |
|---------|-------------|
| `cod_ipa_ente` | Ente che ha effettuato la richiesta |
| `tipo_operazione` | Operazione SOAP richiesta |
| `modalita_routing` | Modalità utilizzata (PU o legacy) |
| `destinazione` | Backend raggiunto (MYPAY o MYPIVOT) |
| `esito` | `OK` oppure `ERRORE` |
| `durata_ms` | Tempo di elaborazione in millisecondi |
| `messaggio_errore` | Dettaglio errore (solo in caso di fallimento) |

### Cache in-memory

La tabella `mwpay_ente_config` viene mantenuta in una **cache in-memory** con TTL configurabile (default: 5 minuti) per evitare una query al database a ogni richiesta SOAP. La cache si rinnova automaticamente e, in caso di errore DB durante il refresh, mantiene i dati precedenti garantendo continuità di servizio (**stale-while-revalidate**).

---

## 8. Stato attuale e prossimi passi

### Funzionalità completate

| Area | Stato | Descrizione |
|------|-------|-------------|
| Struttura e fondazioni | ✅ Completata | Progetto Maven multi-modulo, pacchetti Java, configurazione Spring |
| Autenticazione OAuth2 | ✅ Completata | Token con cache, rinnovo automatico, thread-safety |
| Endpoint SOAP prototipo | ✅ Completato | `pivotSILAutorizzaImportFlussoTesoreria` — flusso completo funzionante |
| Routing dinamico | ✅ Completato | Decisione a 2 dimensioni (path + DB), migrazione enti senza riavvio |
| Client HTTP (PU + legacy) | ✅ Completati | Inoltro con OAuth2 verso PU e forward diretto verso backend legacy |
| Resilienza | ✅ Completata | Circuit breaker e retry su entrambi i client |
| Monitoraggio | ✅ Completato | Health check, metriche Micrometer, endpoint Actuator |
| Persistenza | ✅ Completata | PostgreSQL, Jdbi, tabelle routing e log transazionale, cache TTL |
| Gestione errori | ✅ Completata | SOAP Fault strutturati con 5 codici di errore |
| Log transazionale | ✅ Completato | Ogni transazione registrata su DB con esito, durata e dettagli |
| Copertura test | ✅ 124 test | Tutti i componenti principali coperti, BUILD SUCCESS |

### In attesa

| Area | Stato | Dipendenza |
|------|-------|------------|
| **Endpoint SOAP completi** | ⬜ Bloccata | Richiede il censimento degli endpoint dai team mypay e mypivot. Attualmente è implementato solo l'endpoint prototipo `pivotSILAutorizzaImportFlussoTesoreria`. |

### Non ancora implementato (previsto per fasi future)

| Area | Note |
|------|------|
| Logica di business (riconciliazione, tesoreria) | Il middleware fa solo forwarding del payload, senza trasformazione |
| Validazione business dei dati SOAP | Il payload viene inoltrato così com'è |
| Endpoint aggiuntivi (mypay, mypivot) | In attesa del censimento dai team backend |
| Profili UAT e Produzione | Da creare quando necessario per il deployment |
| Test di integrazione end-to-end | Attualmente solo test unitari con mock |
| Rate limiting per SIL | Potrebbe essere necessario con il crescere del numero di enti |

---

## 9. Glossario

| Termine | Significato |
|---------|-------------|
| **SIL** | Sistemi Informativi Locali — i software degli enti pubblici che inviano richieste al middleware |
| **Piattaforma Unitaria (PU)** | Sistema di pagoPA che gestisce i pagamenti elettronici degli enti pubblici |
| **pagoPA** | Piattaforma nazionale per i pagamenti verso la Pubblica Amministrazione |
| **OAuth2 Client Credentials** | Flusso di autenticazione machine-to-machine: il client si identifica con `client_id` e `client_secret` e ottiene un token di accesso |
| **SOAP** | Protocollo per lo scambio di messaggi XML tra sistemi |
| **codIpaEnte** | Codice IPA dell'ente pubblico — identificativo univoco nel sistema |
| **Circuit Breaker** | Pattern di resilienza che interrompe le chiamate a un servizio non disponibile per evitare cascate di errori |
| **SpringLine2** | Framework proprietario di ARIA S.p.A. (Regione Lombardia) che estende Spring Boot |
| **Jdbi** | Libreria Java leggera per l'accesso al database relazionale, alternativa a JPA/Hibernate |
| **Actuator** | Modulo Spring Boot che espone endpoint HTTP per il monitoraggio dell'applicazione |
| **mypay / mypivot** | Backend legacy regionali per la gestione di pagamenti e riconciliazioni |
