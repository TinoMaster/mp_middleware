package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.EnteConfig;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per {@link EnteConfigCacheService}.
 *
 * <p>Verifica:
 * <ul>
 *   <li>Caricamento iniziale della cache dal repository</li>
 *   <li>Lookup per ente e tipo operazione</li>
 *   <li>Verifica censimento ente</li>
 *   <li>Refresh della cache alla scadenza del TTL</li>
 *   <li>Resilienza in caso di errore DB durante il refresh</li>
 *   <li>Refresh forzato</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EnteConfigCacheServiceTest {

    @Mock
    private EnteConfigRepository repository;

    private EnteConfigCacheService cacheService;

    /** Configurazione di esempio: R_LOMBARDIA su PU. */
    private EnteConfig configLombardiaPU;

    /** Configurazione di esempio: COMUNE_MILANO su LEGACY. */
    private EnteConfig configMilanoLegacy;

    @BeforeEach
    void setUp() throws Exception {
        configLombardiaPU = new EnteConfig(
                1L, "R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria",
                ModalitaRouting.PIATTAFORMA_UNITARIA, true, null,
                LocalDateTime.now(), LocalDateTime.now());

        configMilanoLegacy = new EnteConfig(
                2L, "COMUNE_MILANO", "pivotSILAutorizzaImportFlussoTesoreria",
                ModalitaRouting.LEGACY, true, null,
                LocalDateTime.now(), LocalDateTime.now());

        // Configura il repository per restituire le configurazioni di esempio
        when(repository.findAllAttive()).thenReturn(
                List.of(configLombardiaPU, configMilanoLegacy));

        // Crea il servizio e imposta TTL alto per evitare refresh automatici
        cacheService = new EnteConfigCacheService(repository);
        setTtl(cacheService, 3600L); // 1 ora — nessun refresh spontaneo durante i test
        cacheService.init();
    }

    // --- Lookup ---

    @Test
    @DisplayName("findByCodIpaEnteAndTipoOperazione - ente censito restituisce configurazione")
    void find_enteCensito_returnsConfig() {
        Optional<EnteConfig> result = cacheService.findByCodIpaEnteAndTipoOperazione(
                "R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria");

        assertTrue(result.isPresent());
        assertEquals(ModalitaRouting.PIATTAFORMA_UNITARIA, result.get().getModalitaRouting());
    }

    @Test
    @DisplayName("findByCodIpaEnteAndTipoOperazione - ente non censito restituisce vuoto")
    void find_enteNonCensito_returnsEmpty() {
        Optional<EnteConfig> result = cacheService.findByCodIpaEnteAndTipoOperazione(
                "ENTE_INESISTENTE", "pivotSILAutorizzaImportFlussoTesoreria");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByCodIpaEnteAndTipoOperazione - ente censito ma operazione diversa restituisce vuoto")
    void find_enteConOperazioneDiversa_returnsEmpty() {
        Optional<EnteConfig> result = cacheService.findByCodIpaEnteAndTipoOperazione(
                "R_LOMBARDIA", "operazioneInesistente");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByCodIpaEnteAndTipoOperazione - COMUNE_MILANO restituisce LEGACY")
    void find_milanoLegacy_returnsLegacy() {
        Optional<EnteConfig> result = cacheService.findByCodIpaEnteAndTipoOperazione(
                "COMUNE_MILANO", "pivotSILAutorizzaImportFlussoTesoreria");

        assertTrue(result.isPresent());
        assertEquals(ModalitaRouting.LEGACY, result.get().getModalitaRouting());
    }

    // --- isEnteCensito ---

    @Test
    @DisplayName("isEnteCensito - ente presente restituisce true")
    void isEnteCensito_presente_returnsTrue() {
        assertTrue(cacheService.isEnteCensito("R_LOMBARDIA"));
    }

    @Test
    @DisplayName("isEnteCensito - ente assente restituisce false")
    void isEnteCensito_assente_returnsFalse() {
        assertFalse(cacheService.isEnteCensito("ENTE_INESISTENTE"));
    }

    // --- size ---

    @Test
    @DisplayName("size - restituisce il numero di configurazioni in cache")
    void size_returnsCorrectCount() {
        assertEquals(2, cacheService.size());
    }

    // --- Refresh TTL ---

    @Test
    @DisplayName("Cache scaduta - il refresh ricarica dal DB")
    void cacheScaduta_triggerRefresh() throws Exception {
        // Cambia la risposta del repository
        EnteConfig nuovaConfig = new EnteConfig(
                3L, "NUOVO_ENTE", "operazioneNuova",
                ModalitaRouting.PIATTAFORMA_UNITARIA, true, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(repository.findAllAttive()).thenReturn(List.of(nuovaConfig));

        // Forza la scadenza della cache impostando TTL a 0
        setTtl(cacheService, 0L);

        // La prossima chiamata dovrebbe triggerare il refresh
        Optional<EnteConfig> result = cacheService.findByCodIpaEnteAndTipoOperazione(
                "NUOVO_ENTE", "operazioneNuova");

        assertTrue(result.isPresent());
        assertEquals("NUOVO_ENTE", result.get().getCodIpaEnte());
        // La vecchia configurazione non c'e' piu'
        assertFalse(cacheService.findByCodIpaEnteAndTipoOperazione(
                "R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria").isPresent());
    }

    // --- Resilienza errore DB ---

    @Test
    @DisplayName("Errore DB durante refresh - la cache stale viene mantenuta")
    void erroreDb_mantieneCacheStale() throws Exception {
        // Prima verifica che la cache funzioni
        assertEquals(2, cacheService.size());

        // Simula errore DB al prossimo refresh
        when(repository.findAllAttive()).thenThrow(new RuntimeException("DB non raggiungibile"));

        // Forza la scadenza e tenta il refresh
        setTtl(cacheService, 0L);

        // La cache stale deve essere ancora disponibile
        Optional<EnteConfig> result = cacheService.findByCodIpaEnteAndTipoOperazione(
                "R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria");
        assertTrue(result.isPresent());
        assertEquals(2, cacheService.size());
    }

    // --- Refresh forzato ---

    @Test
    @DisplayName("forceRefresh - ricarica la cache indipendentemente dal TTL")
    void forceRefresh_ricaricaSempreDalDb() {
        // Cambia la risposta del repository
        when(repository.findAllAttive()).thenReturn(Collections.emptyList());

        cacheService.forceRefresh();

        assertEquals(0, cacheService.size());
    }

    // --- Init con cache vuota ---

    @Test
    @DisplayName("init - cache vuota se nessun record attivo in DB")
    void init_cacheVuota() throws Exception {
        when(repository.findAllAttive()).thenReturn(Collections.emptyList());

        EnteConfigCacheService emptyCacheService = new EnteConfigCacheService(repository);
        setTtl(emptyCacheService, 3600L);
        emptyCacheService.init();

        assertEquals(0, emptyCacheService.size());
        assertFalse(emptyCacheService.isEnteCensito("R_LOMBARDIA"));
    }

    // --- Utility: imposta il TTL via reflection ---

    /**
     * Imposta il valore del campo {@code ttlSeconds} via reflection,
     * dato che nei test non c'e' il contesto Spring per iniettare {@code @Value}.
     */
    private void setTtl(EnteConfigCacheService service, long ttl) throws Exception {
        Field field = EnteConfigCacheService.class.getDeclaredField("ttlSeconds");
        field.setAccessible(true);
        field.setLong(service, ttl);
    }
}
