package it.ariaspa.mypay.mypaycore.api.health;

import it.ariaspa.mypay.mypaycore.api.repository.EnteConfigCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per {@link EnteConfigHealthIndicator}.
 *
 * <p>Verifica i tre stati possibili dell'health indicator:
 * <ul>
 *   <li>UP — cache contiene almeno una configurazione ente</li>
 *   <li>DOWN — cache vuota (nessun ente configurato)</li>
 *   <li>DOWN con eccezione — errore durante il controllo</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EnteConfigHealthIndicatorTest {

    @Mock
    private EnteConfigCacheService enteConfigCacheService;

    private EnteConfigHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new EnteConfigHealthIndicator(enteConfigCacheService);
    }

    @Test
    @DisplayName("Cache con enti configurati — stato UP con dettagli")
    void health_cacheConEnti_statoUp() {
        when(enteConfigCacheService.size()).thenReturn(5);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("Configurazione enti attiva", health.getDetails().get("stato"));
        assertEquals(5, health.getDetails().get("entiConfigurati"));
    }

    @Test
    @DisplayName("Cache con un solo ente — stato UP")
    void health_cacheConUnEnte_statoUp() {
        when(enteConfigCacheService.size()).thenReturn(1);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(1, health.getDetails().get("entiConfigurati"));
    }

    @Test
    @DisplayName("Cache vuota — stato DOWN")
    void health_cacheVuota_statoDown() {
        when(enteConfigCacheService.size()).thenReturn(0);

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(0, health.getDetails().get("entiConfigurati"));
        assertTrue(health.getDetails().get("stato").toString()
                .contains("Nessun ente configurato"));
    }

    @Test
    @DisplayName("Eccezione durante il controllo — stato DOWN con eccezione")
    void health_eccezione_statoDownConErrore() {
        when(enteConfigCacheService.size())
                .thenThrow(new RuntimeException("Connessione DB persa"));

        Health health = healthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().get("stato").toString()
                .contains("Errore durante il controllo"));
        assertNotNull(health.getDetails().get("error"));
    }
}
