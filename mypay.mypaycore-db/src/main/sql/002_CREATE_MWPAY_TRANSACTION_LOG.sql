-- =============================================================================
-- 002_CREATE_MWPAY_TRANSACTION_LOG.sql
-- Tabella di log transazionale: registra ogni richiesta SOAP processata
-- dal middleware con informazioni di routing, esito e durata.
--
-- Lo scopo e' garantire tracciabilita completa per auditing e diagnostica.
-- Prefisso tabelle middleware: mwpay_
-- =============================================================================

CREATE TABLE mwpay_transaction_log (
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

    -- Vincolo: modalita_routing accetta solo valori noti
    CONSTRAINT chk_txlog_modalita_routing CHECK (
        modalita_routing IN ('PIATTAFORMA_UNITARIA', 'LEGACY')
    ),

    -- Vincolo: destinazione accetta solo backend noti
    CONSTRAINT chk_txlog_destinazione CHECK (
        destinazione IN ('MYPAY', 'MYPIVOT')
    ),

    -- Vincolo: esito accetta solo valori noti
    CONSTRAINT chk_txlog_esito CHECK (
        esito IN ('OK', 'ERRORE')
    )
);

-- Indice per ricerca per ente e timestamp (reporting per ente)
CREATE INDEX idx_mwpay_txlog_ente_ts
    ON mwpay_transaction_log (cod_ipa_ente, timestamp_richiesta DESC);

-- Indice per ricerca per timestamp (reporting temporale, pulizia dati vecchi)
CREATE INDEX idx_mwpay_txlog_timestamp
    ON mwpay_transaction_log (timestamp_richiesta DESC);

-- Indice per ricerca per esito (monitoraggio errori)
CREATE INDEX idx_mwpay_txlog_esito
    ON mwpay_transaction_log (esito)
    WHERE esito = 'ERRORE';

COMMENT ON TABLE mwpay_transaction_log IS
    'Log transazionale del middleware: ogni richiesta SOAP processata genera un record '
    'con informazioni di routing, esito e durata per auditing e diagnostica.';

COMMENT ON COLUMN mwpay_transaction_log.destinazione IS
    'Backend di destinazione determinato dal path (MYPAY o MYPIVOT)';

COMMENT ON COLUMN mwpay_transaction_log.esito IS
    'Esito della transazione: OK o ERRORE';

COMMENT ON COLUMN mwpay_transaction_log.durata_ms IS
    'Durata totale della transazione in millisecondi (include tempo di processing del middleware)';
