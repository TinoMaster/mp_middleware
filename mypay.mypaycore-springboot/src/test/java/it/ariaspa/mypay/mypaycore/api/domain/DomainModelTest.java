package it.ariaspa.mypay.mypaycore.api.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per le classi di dominio {@link EnteConfig}, {@link TransactionLog}
 * e l'enum {@link ModalitaRouting}.
 *
 * <p>Verifica:
 * <ul>
 *   <li>Valori dell'enum {@link ModalitaRouting}</li>
 *   <li>Costruttori, getter e setter di {@link EnteConfig}</li>
 *   <li>Costruttori, getter e setter di {@link TransactionLog}</li>
 *   <li>Metodo {@code toString()} di entrambe le classi</li>
 * </ul>
 */
class DomainModelTest {

    // --- ModalitaRouting ---

    @Test
    @DisplayName("ModalitaRouting - contiene esattamente PIATTAFORMA_UNITARIA e LEGACY")
    void modalitaRouting_hasTwoValues() {
        ModalitaRouting[] values = ModalitaRouting.values();
        assertEquals(2, values.length);
        assertEquals(ModalitaRouting.PIATTAFORMA_UNITARIA, ModalitaRouting.valueOf("PIATTAFORMA_UNITARIA"));
        assertEquals(ModalitaRouting.LEGACY, ModalitaRouting.valueOf("LEGACY"));
    }

    @Test
    @DisplayName("ModalitaRouting - valueOf con valore non valido lancia IllegalArgumentException")
    void modalitaRouting_invalidValue_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> ModalitaRouting.valueOf("INVALIDO"));
    }

    // --- EnteConfig ---

    @Test
    @DisplayName("EnteConfig - costruttore vuoto crea istanza con valori null/default")
    void enteConfig_defaultConstructor() {
        EnteConfig config = new EnteConfig();
        assertNull(config.getId());
        assertNull(config.getCodIpaEnte());
        assertNull(config.getTipoOperazione());
        assertNull(config.getModalitaRouting());
        assertFalse(config.isAttivo());
        assertNull(config.getNote());
        assertNull(config.getDataCreazione());
        assertNull(config.getDataAggiornamento());
    }

    @Test
    @DisplayName("EnteConfig - costruttore completo popola tutti i campi")
    void enteConfig_fullConstructor() {
        LocalDateTime now = LocalDateTime.now();
        EnteConfig config = new EnteConfig(
                1L, "R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria",
                ModalitaRouting.PIATTAFORMA_UNITARIA, true, "Ente pilota",
                now, now);

        assertEquals(1L, config.getId());
        assertEquals("R_LOMBARDIA", config.getCodIpaEnte());
        assertEquals("pivotSILAutorizzaImportFlussoTesoreria", config.getTipoOperazione());
        assertEquals(ModalitaRouting.PIATTAFORMA_UNITARIA, config.getModalitaRouting());
        assertTrue(config.isAttivo());
        assertEquals("Ente pilota", config.getNote());
        assertEquals(now, config.getDataCreazione());
        assertEquals(now, config.getDataAggiornamento());
    }

    @Test
    @DisplayName("EnteConfig - setter aggiornano correttamente i campi")
    void enteConfig_setters() {
        EnteConfig config = new EnteConfig();
        config.setId(42L);
        config.setCodIpaEnte("COMUNE_MILANO");
        config.setTipoOperazione("operazioneTest");
        config.setModalitaRouting(ModalitaRouting.LEGACY);
        config.setAttivo(true);
        config.setNote("test note");

        assertEquals(42L, config.getId());
        assertEquals("COMUNE_MILANO", config.getCodIpaEnte());
        assertEquals("operazioneTest", config.getTipoOperazione());
        assertEquals(ModalitaRouting.LEGACY, config.getModalitaRouting());
        assertTrue(config.isAttivo());
        assertEquals("test note", config.getNote());
    }

    @Test
    @DisplayName("EnteConfig - toString contiene i campi principali")
    void enteConfig_toString() {
        EnteConfig config = new EnteConfig();
        config.setId(1L);
        config.setCodIpaEnte("R_LOMBARDIA");
        config.setTipoOperazione("pivotSIL");
        config.setModalitaRouting(ModalitaRouting.PIATTAFORMA_UNITARIA);
        config.setAttivo(true);

        String str = config.toString();
        assertTrue(str.contains("R_LOMBARDIA"));
        assertTrue(str.contains("pivotSIL"));
        assertTrue(str.contains("PIATTAFORMA_UNITARIA"));
        assertTrue(str.contains("attivo=true"));
    }

    // --- TransactionLog ---

    @Test
    @DisplayName("TransactionLog - costruttore vuoto crea istanza con valori null/default")
    void transactionLog_defaultConstructor() {
        TransactionLog log = new TransactionLog();
        assertNull(log.getId());
        assertNull(log.getCodIpaEnte());
        assertNull(log.getModalitaRouting());
        assertNull(log.getDestinazione());
        assertNull(log.getEsito());
        assertNull(log.getDurataMs());
    }

    @Test
    @DisplayName("TransactionLog - costruttore completo popola tutti i campi")
    void transactionLog_fullConstructor() {
        LocalDateTime now = LocalDateTime.now();
        TransactionLog log = new TransactionLog(
                1L, "R_LOMBARDIA", "pivotSIL",
                ModalitaRouting.PIATTAFORMA_UNITARIA, "MYPIVOT",
                "/ws/pivot/PagamentiTelematici", 200,
                "OK", null, 150L, now);

        assertEquals(1L, log.getId());
        assertEquals("R_LOMBARDIA", log.getCodIpaEnte());
        assertEquals("pivotSIL", log.getTipoOperazione());
        assertEquals(ModalitaRouting.PIATTAFORMA_UNITARIA, log.getModalitaRouting());
        assertEquals("MYPIVOT", log.getDestinazione());
        assertEquals("/ws/pivot/PagamentiTelematici", log.getPathRichiesta());
        assertEquals(200, log.getHttpStatusRisposta());
        assertEquals("OK", log.getEsito());
        assertNull(log.getMessaggioErrore());
        assertEquals(150L, log.getDurataMs());
        assertEquals(now, log.getTimestampRichiesta());
    }

    @Test
    @DisplayName("TransactionLog - setter per campo errore")
    void transactionLog_errorFields() {
        TransactionLog log = new TransactionLog();
        log.setEsito("ERRORE");
        log.setMessaggioErrore("Timeout connessione backend");
        log.setHttpStatusRisposta(null);

        assertEquals("ERRORE", log.getEsito());
        assertEquals("Timeout connessione backend", log.getMessaggioErrore());
        assertNull(log.getHttpStatusRisposta());
    }

    @Test
    @DisplayName("TransactionLog - toString contiene i campi principali")
    void transactionLog_toString() {
        TransactionLog log = new TransactionLog();
        log.setId(99L);
        log.setCodIpaEnte("COMUNE_MILANO");
        log.setTipoOperazione("operazione");
        log.setModalitaRouting(ModalitaRouting.LEGACY);
        log.setDestinazione("MYPAY");
        log.setEsito("OK");
        log.setDurataMs(200L);

        String str = log.toString();
        assertTrue(str.contains("COMUNE_MILANO"));
        assertTrue(str.contains("LEGACY"));
        assertTrue(str.contains("OK"));
        assertTrue(str.contains("200"));
    }
}
