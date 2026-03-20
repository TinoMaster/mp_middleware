package it.ariaspa.mypay.mypaycore.api.metrics;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.repository.EnteConfigCacheService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per {@link MiddlewareMetricsService}.
 *
 * <p>Utilizza {@link SimpleMeterRegistry} per verificare che le metriche vengano
 * correttamente registrate senza necessita di un contesto Spring completo.
 *
 * <p>Verifica:
 * <ul>
 *   <li>Registrazione gauge per enti configurati</li>
 *   <li>Contatore richieste incrementato su successo e errore</li>
 *   <li>Timer durata registrato correttamente</li>
 *   <li>Tag corretti per tutte le metriche</li>
 *   <li>Gestione corretta di RoutingDecision null (errore pre-routing)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MiddlewareMetricsServiceTest {

    @Mock
    private EnteConfigCacheService enteConfigCacheService;

    private SimpleMeterRegistry meterRegistry;
    private MiddlewareMetricsService service;

    /** Decisione di routing verso la PU */
    private static final RoutingDecision DECISION_PU = new RoutingDecision(
            BackendDestinatario.MYPIVOT, ModalitaRouting.PIATTAFORMA_UNITARIA, "http://localhost:8081");

    /** Decisione di routing verso il backend legacy */
    private static final RoutingDecision DECISION_LEGACY = new RoutingDecision(
            BackendDestinatario.MYPAY, ModalitaRouting.LEGACY, "http://localhost:8080");

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // lenient perche' il gauge registra un lambda che legge size() solo quando
        // viene interrogato, non in tutti i test — Mockito strict lo segnala come unused
        lenient().when(enteConfigCacheService.size()).thenReturn(3);
        service = new MiddlewareMetricsService(meterRegistry, enteConfigCacheService);
    }

    @Nested
    @DisplayName("Gauge enti configurati")
    class GaugeEntiConfigurati {

        @Test
        @DisplayName("Gauge registrato con valore corretto dalla cache")
        void gauge_registratoConValoreCorretto() {
            Double value = meterRegistry.get(MiddlewareMetricsService.METRICA_ENTI_CONFIGURATI)
                    .gauge().value();

            assertEquals(3.0, value);
        }

        @Test
        @DisplayName("Gauge si aggiorna quando cambia la dimensione della cache")
        void gauge_siAggiornaConCache() {
            // Simula un cambio nella cache
            when(enteConfigCacheService.size()).thenReturn(10);

            Double value = meterRegistry.get(MiddlewareMetricsService.METRICA_ENTI_CONFIGURATI)
                    .gauge().value();

            assertEquals(10.0, value);
        }
    }

    @Nested
    @DisplayName("Contatore richieste — successo")
    class ContatoreSuccesso {

        @Test
        @DisplayName("registraSuccesso incrementa il contatore con tag corretti")
        void registraSuccesso_incrementaContatore() {
            service.registraSuccesso("ENTE_TEST", "operazione", DECISION_PU, 150L);

            Counter counter = meterRegistry.get(MiddlewareMetricsService.METRICA_RICHIESTE_TOTALI)
                    .tag("ente", "ENTE_TEST")
                    .tag("operazione", "operazione")
                    .tag("modalita", "PIATTAFORMA_UNITARIA")
                    .tag("destinazione", "MYPIVOT")
                    .tag("esito", "OK")
                    .counter();

            assertEquals(1.0, counter.count());
        }

        @Test
        @DisplayName("Chiamate multiple incrementano il contatore")
        void registraSuccesso_chiamateMultiple_incrementano() {
            service.registraSuccesso("ENTE_TEST", "operazione", DECISION_PU, 100L);
            service.registraSuccesso("ENTE_TEST", "operazione", DECISION_PU, 200L);
            service.registraSuccesso("ENTE_TEST", "operazione", DECISION_PU, 300L);

            Counter counter = meterRegistry.get(MiddlewareMetricsService.METRICA_RICHIESTE_TOTALI)
                    .tag("ente", "ENTE_TEST")
                    .tag("esito", "OK")
                    .counter();

            assertEquals(3.0, counter.count());
        }
    }

    @Nested
    @DisplayName("Contatore richieste — errore")
    class ContatoreErrore {

        @Test
        @DisplayName("registraErrore incrementa il contatore con esito ERRORE")
        void registraErrore_incrementaContatore() {
            service.registraErrore("ENTE_ERR", "operazione", DECISION_LEGACY, 50L);

            Counter counter = meterRegistry.get(MiddlewareMetricsService.METRICA_RICHIESTE_TOTALI)
                    .tag("ente", "ENTE_ERR")
                    .tag("esito", "ERRORE")
                    .tag("modalita", "LEGACY")
                    .tag("destinazione", "MYPAY")
                    .counter();

            assertEquals(1.0, counter.count());
        }

        @Test
        @DisplayName("registraErrore con decision null — usa tag SCONOSCIUTA")
        void registraErrore_decisionNull_usaSconosciuta() {
            service.registraErrore("ENTE_ERR", "operazione", null, 30L);

            Counter counter = meterRegistry.get(MiddlewareMetricsService.METRICA_RICHIESTE_TOTALI)
                    .tag("ente", "ENTE_ERR")
                    .tag("esito", "ERRORE")
                    .tag("modalita", "SCONOSCIUTA")
                    .tag("destinazione", "SCONOSCIUTA")
                    .counter();

            assertEquals(1.0, counter.count());
        }

        @Test
        @DisplayName("registraErrore con codIpaEnte null — usa tag 'sconosciuto'")
        void registraErrore_codIpaEnteNull_usaSconosciuto() {
            service.registraErrore(null, null, null, 20L);

            Counter counter = meterRegistry.get(MiddlewareMetricsService.METRICA_RICHIESTE_TOTALI)
                    .tag("ente", "sconosciuto")
                    .tag("operazione", "sconosciuta")
                    .tag("esito", "ERRORE")
                    .counter();

            assertEquals(1.0, counter.count());
        }
    }

    @Nested
    @DisplayName("Timer durata richieste")
    class TimerDurata {

        @Test
        @DisplayName("registraSuccesso registra la durata nel timer")
        void registraSuccesso_registraDurata() {
            service.registraSuccesso("ENTE_TEST", "operazione", DECISION_PU, 250L);

            Timer timer = meterRegistry.get(MiddlewareMetricsService.METRICA_RICHIESTE_DURATA)
                    .tag("operazione", "operazione")
                    .tag("modalita", "PIATTAFORMA_UNITARIA")
                    .tag("destinazione", "MYPIVOT")
                    .timer();

            assertEquals(1, timer.count());
            // Il timer registra in secondi internamente; 250ms = 0.25s
            assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 250.0);
        }

        @Test
        @DisplayName("registraErrore registra la durata nel timer")
        void registraErrore_registraDurata() {
            service.registraErrore("ENTE_TEST", "operazione", DECISION_LEGACY, 100L);

            Timer timer = meterRegistry.get(MiddlewareMetricsService.METRICA_RICHIESTE_DURATA)
                    .tag("operazione", "operazione")
                    .tag("modalita", "LEGACY")
                    .tag("destinazione", "MYPAY")
                    .timer();

            assertEquals(1, timer.count());
        }
    }

    @Nested
    @DisplayName("Nomi delle metriche (costanti)")
    class NomiMetriche {

        @Test
        @DisplayName("Nome metrica richieste totali")
        void nomeMetricaRichiesteTotali() {
            assertEquals("middleware.richieste.totali",
                    MiddlewareMetricsService.METRICA_RICHIESTE_TOTALI);
        }

        @Test
        @DisplayName("Nome metrica durata richieste")
        void nomeMetricaDurataRichieste() {
            assertEquals("middleware.richieste.durata",
                    MiddlewareMetricsService.METRICA_RICHIESTE_DURATA);
        }

        @Test
        @DisplayName("Nome metrica enti configurati")
        void nomeMetricaEntiConfigurati() {
            assertEquals("middleware.enti.configurati",
                    MiddlewareMetricsService.METRICA_ENTI_CONFIGURATI);
        }
    }
}
