-- =============================================================================
-- 007_ALTER_MYGOV_ENTE_CONFIG_PU.sql
-- Allineamento della tabella mygov_ente_config_pu allo schema atteso dal
-- codice Java del middleware (Fase 8 — 25 Mar 2026).
--
-- La tabella originale e' stata creata manualmente con nomi di colonna diversi
-- da quelli usati nel modello Java EnteConfigPu e nei RowMapper/Repository.
-- Questo script rinomina le colonne e aggiunge quelle mancanti.
--
-- Disallineamenti corretti:
--   mygov_ente_config_pu_id → id
--   cod_ipa_ente            → codice_ipa_ente
--   secret                  → client_secret
--   (assente)               → attivo (BOOLEAN DEFAULT TRUE)
--   (assente)               → dt_creazione (TIMESTAMPTZ DEFAULT NOW())
--   (assente)               → dt_ultima_modifica (TIMESTAMPTZ DEFAULT NOW())
-- =============================================================================

-- Passo 1: Rinomina le colonne esistenti
ALTER TABLE mygov_ente_config_pu
    RENAME COLUMN mygov_ente_config_pu_id TO id;

ALTER TABLE mygov_ente_config_pu
    RENAME COLUMN cod_ipa_ente TO codice_ipa_ente;

ALTER TABLE mygov_ente_config_pu
    RENAME COLUMN secret TO client_secret;

-- Passo 2: Aggiungi le colonne mancanti
ALTER TABLE mygov_ente_config_pu
    ADD COLUMN IF NOT EXISTS attivo BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE mygov_ente_config_pu
    ADD COLUMN IF NOT EXISTS dt_creazione TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

ALTER TABLE mygov_ente_config_pu
    ADD COLUMN IF NOT EXISTS dt_ultima_modifica TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- Passo 3: Aggiungi vincolo di unicita' sulla colonna rinominata (se non esiste)
-- Il vincolo previene duplicati: ogni ente ha al massimo una configurazione PU.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_mygov_ente_config_pu_codice_ipa'
    ) THEN
        ALTER TABLE mygov_ente_config_pu
            ADD CONSTRAINT uq_mygov_ente_config_pu_codice_ipa
            UNIQUE (codice_ipa_ente);
    END IF;
END
$$;

-- Passo 4: Imposta NOT NULL sulle colonne che lo richiedono
-- (le colonne originali potrebbero non avere NOT NULL)
ALTER TABLE mygov_ente_config_pu
    ALTER COLUMN codice_ipa_ente SET NOT NULL;

ALTER TABLE mygov_ente_config_pu
    ALTER COLUMN client_id SET NOT NULL;

ALTER TABLE mygov_ente_config_pu
    ALTER COLUMN client_secret SET NOT NULL;

-- Passo 5: Aggiungi indice per ricerca per ente + stato attivazione
CREATE INDEX IF NOT EXISTS idx_mygov_ente_config_pu_ente_attivo
    ON mygov_ente_config_pu (codice_ipa_ente, attivo);

-- Passo 6: Commenti sulle colonne
COMMENT ON TABLE  mygov_ente_config_pu                    IS 'Credenziali OAuth2 per ente verso la Piattaforma Unitaria pagoPA';
COMMENT ON COLUMN mygov_ente_config_pu.id                 IS 'Identificativo univoco del record (chiave surrogata)';
COMMENT ON COLUMN mygov_ente_config_pu.codice_ipa_ente    IS 'Codice IPA dell''ente — chiave esterna verso mygov_ente.cod_ipa_ente';
COMMENT ON COLUMN mygov_ente_config_pu.client_id          IS 'Client ID OAuth2 assegnato dalla PU per questo ente';
COMMENT ON COLUMN mygov_ente_config_pu.client_secret      IS 'Client Secret OAuth2 (testo in chiaro — da cifrare in futuro con Jasypt)';
COMMENT ON COLUMN mygov_ente_config_pu.attivo             IS 'Se FALSE il middleware usa il flusso legacy anche se il record esiste';
COMMENT ON COLUMN mygov_ente_config_pu.dt_creazione       IS 'Data e ora di creazione del record';
COMMENT ON COLUMN mygov_ente_config_pu.dt_ultima_modifica IS 'Data e ora dell''ultimo aggiornamento del record';
