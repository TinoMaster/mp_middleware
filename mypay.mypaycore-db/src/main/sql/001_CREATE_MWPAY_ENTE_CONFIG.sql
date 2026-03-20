-- =============================================================================
-- 001_CREATE_MWPAY_ENTE_CONFIG.sql
-- Tabella di configurazione routing per ente e tipo operazione.
--
-- Ogni record indica se, per un determinato ente (cod_ipa_ente) e una specifica
-- operazione SOAP (tipo_operazione), la richiesta deve essere instradata verso
-- la Piattaforma Unitaria (con OAuth2) o verso il backend legacy (forward diretto).
--
-- Prefisso tabelle middleware: mwpay_
-- =============================================================================

CREATE TABLE mwpay_ente_config (
    id                 BIGSERIAL    PRIMARY KEY,
    cod_ipa_ente       VARCHAR(50)  NOT NULL,
    tipo_operazione    VARCHAR(100) NOT NULL,
    modalita_routing   VARCHAR(30)  NOT NULL,
    attivo             BOOLEAN      NOT NULL DEFAULT TRUE,
    note               TEXT,
    data_creazione     TIMESTAMP    NOT NULL DEFAULT NOW(),
    data_aggiornamento TIMESTAMP    NOT NULL DEFAULT NOW(),

    -- Vincolo: un solo record per coppia ente + operazione
    CONSTRAINT uq_ente_operazione UNIQUE (cod_ipa_ente, tipo_operazione),

    -- Vincolo: modalita_routing accetta solo valori noti
    CONSTRAINT chk_modalita_routing CHECK (
        modalita_routing IN ('PIATTAFORMA_UNITARIA', 'LEGACY')
    )
);

-- Indice per ricerca rapida per ente (usato dalla cache e dalle query di routing)
CREATE INDEX idx_mwpay_ente_config_cod_ipa
    ON mwpay_ente_config (cod_ipa_ente);

-- Indice per le query che filtrano solo i record attivi
CREATE INDEX idx_mwpay_ente_config_attivo
    ON mwpay_ente_config (attivo)
    WHERE attivo = TRUE;

COMMENT ON TABLE mwpay_ente_config IS
    'Configurazione di routing del middleware: per ogni ente e operazione SOAP, '
    'indica se instradare verso la Piattaforma Unitaria o il backend legacy.';

COMMENT ON COLUMN mwpay_ente_config.cod_ipa_ente IS
    'Codice IPA dell''ente pubblico (es. R_LOMBARDIA)';

COMMENT ON COLUMN mwpay_ente_config.tipo_operazione IS
    'Tipo di operazione SOAP — corrisponde al local part del messaggio '
    '(es. pivotSILAutorizzaImportFlussoTesoreria)';

COMMENT ON COLUMN mwpay_ente_config.modalita_routing IS
    'Modalita di instradamento: PIATTAFORMA_UNITARIA (con OAuth2) o LEGACY (forward diretto)';

COMMENT ON COLUMN mwpay_ente_config.attivo IS
    'Flag di attivazione: consente di disabilitare temporaneamente una regola senza eliminarla';
