-- =============================================================================
-- Script 004: Creazione tabella mygov_ente_config_pu
-- =============================================================================
-- Tabella che associa a ogni ente (mygov_ente) le credenziali OAuth2
-- specifiche per l'accesso alla Piattaforma Unitaria (pagoPA).
--
-- Relazione 1:1 con mygov_ente tramite codice IPA.
-- Se un ente ha un record attivo in questa tabella, il middleware
-- instrada le sue richieste verso la Piattaforma Unitaria (flusso OAuth2).
-- Altrimenti, le richieste vengono inoltrate al backend legacy.
-- =============================================================================

CREATE TABLE IF NOT EXISTS mygov_ente_config_pu (
    id                  BIGSERIAL       PRIMARY KEY,

    -- Codice IPA dell'ente (FK verso mygov_ente.cod_ipa_ente)
    codice_ipa_ente     VARCHAR(80)     NOT NULL,

    -- Credenziali OAuth2 per il Client Credentials Flow verso la PU
    client_id           VARCHAR(255)    NOT NULL,
    client_secret       VARCHAR(500)    NOT NULL,

    -- Flag di attivazione: se FALSE, il middleware usa il flusso legacy
    attivo              BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Timestamp di audit
    dt_creazione        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    dt_ultima_modifica  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Vincolo di unicita: ogni ente ha al massimo una configurazione PU
    CONSTRAINT uq_mygov_ente_config_pu_codice_ipa UNIQUE (codice_ipa_ente),

    -- Integrita referenziale verso la tabella degli enti
    CONSTRAINT fk_mygov_ente_config_pu_ente
        FOREIGN KEY (codice_ipa_ente)
        REFERENCES mygov_ente(cod_ipa_ente)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
);

-- Indice per le query di ricerca per codice IPA (gia' coperto dall'unique, ma esplicito per chiarezza)
CREATE INDEX IF NOT EXISTS idx_mygov_ente_config_pu_ente_attivo
    ON mygov_ente_config_pu (codice_ipa_ente, attivo);

-- Commenti sulle colonne
COMMENT ON TABLE  mygov_ente_config_pu                    IS 'Credenziali OAuth2 per ente verso la Piattaforma Unitaria pagoPA';
COMMENT ON COLUMN mygov_ente_config_pu.codice_ipa_ente    IS 'Codice IPA dell''ente — chiave esterna verso mygov_ente.cod_ipa_ente';
COMMENT ON COLUMN mygov_ente_config_pu.client_id          IS 'Client ID OAuth2 assegnato alla PU per questo ente';
COMMENT ON COLUMN mygov_ente_config_pu.client_secret      IS 'Client Secret OAuth2 (testo in chiaro — da cifrare in futuro con Jasypt)';
COMMENT ON COLUMN mygov_ente_config_pu.attivo             IS 'Se FALSE il middleware usa il flusso legacy anche se il record esiste';
COMMENT ON COLUMN mygov_ente_config_pu.dt_creazione       IS 'Data e ora di creazione del record';
COMMENT ON COLUMN mygov_ente_config_pu.dt_ultima_modifica IS 'Data e ora dell''ultimo aggiornamento del record';
