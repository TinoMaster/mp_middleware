-- =============================================================================
-- 002_CREATE_MWPAY_TRANSACTION_LOG.sql
-- Tabella di log transazionale: registra ogni richiesta SOAP processata
-- dal middleware con informazioni di routing, esito e durata.
--
-- Lo scopo e' garantire tracciabilita' completa per auditing e diagnostica.
-- Prefisso tabelle: mygov_ (allineato alle convenzioni del progetto mygov)
--
-- Aggiornamento Fase 8 (25 Mar 2026):
--   - Aggiunto 'SCONOSCIUTA' ai vincoli CHECK su modalita_routing e destinazione
--     per supportare il logging di errori pre-routing (quando la decisione di
--     routing non e' ancora stata presa, es. ente non trovato nell'header SOAP).
-- =============================================================================

CREATE TABLE IF NOT EXISTS mygov_mw_transaction_log (
    id                   BIGSERIAL    PRIMARY KEY,
    cod_ipa_ente         VARCHAR(50)  NOT NULL,
    tipo_operazione      VARCHAR(100) NOT NULL,
    modalita_routing     VARCHAR(30)  NOT NULL,
    destinazione         VARCHAR(30)  NOT NULL,
    path_richiesta       VARCHAR(500) NOT NULL,
    http_status_risposta INTEGER,
    esito                VARCHAR(20)  NOT NULL,
    messaggio_errore     TEXT,
    durata_ms            BIGINT,
    timestamp_richiesta  TIMESTAMP    NOT NULL DEFAULT NOW(),

    -- Vincolo: modalita_routing accetta valori noti + SCONOSCIUTA (errori pre-routing)
    CONSTRAINT chk_txlog_modalita_routing CHECK (
        modalita_routing IN ('PIATTAFORMA_UNITARIA', 'LEGACY', 'SCONOSCIUTA')
    ),

    -- Vincolo: destinazione accetta backend noti + SCONOSCIUTA (errori pre-routing)
    CONSTRAINT chk_txlog_destinazione CHECK (
        destinazione IN ('MYPAY', 'MYPIVOT', 'SCONOSCIUTA')
    ),

    -- Vincolo: esito accetta solo valori noti
    CONSTRAINT chk_txlog_esito CHECK (
        esito IN ('OK', 'ERRORE')
    )
);

-- Indice per ricerca per ente e timestamp (reporting per ente)
CREATE INDEX IF NOT EXISTS idx_mygov_mw_txlog_ente_ts
    ON mygov_mw_transaction_log (cod_ipa_ente, timestamp_richiesta DESC);

-- Indice per ricerca per timestamp (reporting temporale, pulizia dati vecchi)
CREATE INDEX IF NOT EXISTS idx_mygov_mw_txlog_timestamp
    ON mygov_mw_transaction_log (timestamp_richiesta DESC);

-- Indice per ricerca per esito (monitoraggio errori)
CREATE INDEX IF NOT EXISTS idx_mygov_mw_txlog_esito
    ON mygov_mw_transaction_log (esito)
    WHERE esito = 'ERRORE';

COMMENT ON TABLE mygov_mw_transaction_log IS
    'Log transazionale del middleware: ogni richiesta SOAP processata genera un record '
    'con informazioni di routing, esito e durata per auditing e diagnostica.';

COMMENT ON COLUMN mygov_mw_transaction_log.destinazione IS
    'Backend di destinazione determinato dal path (MYPAY, MYPIVOT o SCONOSCIUTA se pre-routing)';

COMMENT ON COLUMN mygov_mw_transaction_log.modalita_routing IS
    'Modalita di routing (PIATTAFORMA_UNITARIA, LEGACY o SCONOSCIUTA se pre-routing)';

COMMENT ON COLUMN mygov_mw_transaction_log.esito IS
    'Esito della transazione: OK o ERRORE';

COMMENT ON COLUMN mygov_mw_transaction_log.durata_ms IS
    'Durata totale della transazione in millisecondi (include tempo di processing del middleware)';
