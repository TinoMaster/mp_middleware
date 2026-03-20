-- =============================================================================
-- 003_INSERT_ENTE_CONFIG_EXAMPLE.sql
-- Dati di esempio per sviluppo e test manuali.
--
-- ATTENZIONE: questo script e' solo per l'ambiente di sviluppo.
-- In produzione le configurazioni vanno inserite manualmente dagli amministratori.
-- =============================================================================

-- Esempio 1: Ente R_LOMBARDIA con riconciliazione su Piattaforma Unitaria
INSERT INTO mwpay_ente_config (cod_ipa_ente, tipo_operazione, modalita_routing, attivo, note)
VALUES ('R_LOMBARDIA', 'pivotSILAutorizzaImportFlussoTesoreria', 'PIATTAFORMA_UNITARIA', TRUE,
        'Ente pilota — migrato alla PU per riconciliazione flussi tesoreria');

-- Esempio 2: Ente COMUNE_MILANO con riconciliazione ancora su legacy
INSERT INTO mwpay_ente_config (cod_ipa_ente, tipo_operazione, modalita_routing, attivo, note)
VALUES ('COMUNE_MILANO', 'pivotSILAutorizzaImportFlussoTesoreria', 'LEGACY', TRUE,
        'Ente non ancora migrato — forward diretto a mypivot');

-- Esempio 3: Ente R_LOMBARDIA con una seconda operazione su PU
-- (tipo_operazione di esempio — da sostituire con i valori reali quando disponibili)
INSERT INTO mwpay_ente_config (cod_ipa_ente, tipo_operazione, modalita_routing, attivo, note)
VALUES ('R_LOMBARDIA', 'pivotSILChiediStatoImportFlusso', 'PIATTAFORMA_UNITARIA', TRUE,
        'Ente pilota — anche la verifica stato flusso su PU');

-- Esempio 4: Configurazione disabilitata (per dimostrare il flag attivo)
INSERT INTO mwpay_ente_config (cod_ipa_ente, tipo_operazione, modalita_routing, attivo, note)
VALUES ('COMUNE_BERGAMO', 'pivotSILAutorizzaImportFlussoTesoreria', 'PIATTAFORMA_UNITARIA', FALSE,
        'Disabilitato temporaneamente — in attesa di test');
