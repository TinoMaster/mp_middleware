-- =============================================================================
-- Script 005: Rimozione tabella mwpay_ente_config (obsoleta)
-- =============================================================================
-- La tabella mwpay_ente_config e' stata sostituita dalla combinazione:
--   mygov_ente            (anagrafica enti, condivisa con mypay/mypivot)
--   mygov_ente_config_pu  (credenziali OAuth2 per-ente verso la PU)
--
-- Il routing non e' piu' basato su (ente + tipoOperazione) ma esclusivamente
-- sulla presenza/assenza di una configurazione PU attiva per l'ente.
--
-- ATTENZIONE: eseguire questo script DOPO aver eseguito 004_CREATE_MYGOV_ENTE_CONFIG_PU.sql
-- e dopo aver migrato eventuali dati necessari.
-- =============================================================================

DROP TABLE IF EXISTS mwpay_ente_config;
