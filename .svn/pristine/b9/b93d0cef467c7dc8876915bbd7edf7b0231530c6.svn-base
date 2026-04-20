package it.ariaspa.mypay.mypaycore.api.metrics;

import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Servizio per la raccolta delle metriche Micrometer del middleware.
 *
 * <p>Espone metriche personalizzate tramite Spring Boot Actuator per il monitoraggio
 * operativo del gateway SOAP. Le metriche sono accessibili su
 * {@code /actuator/metrics/middleware.*}.
 *
 * <p>Metriche registrate:
 * <ul>
 *   <li><strong>{@code middleware.richieste.totali}</strong> — contatore delle richieste SOAP
 *       processate, con tag: {@code ente}, {@code modalita}, {@code destinazione},
 *       {@code esito}</li>
 *   <li><strong>{@code middleware.richieste.durata}</strong> — istogramma della durata
 *       delle richieste in millisecondi, con tag: {@code modalita}, {@code destinazione}</li>
 *   <li><strong>{@code middleware.enti.totali}</strong> — gauge del numero totale di
 *       enti censiti in cache</li>
 *   <li><strong>{@code middleware.enti.piattaforma.unitaria}</strong> — gauge del numero
 *       di enti con configurazione PU attiva</li>
 * </ul>
 *
 * <p>I tag permettono di filtrare e aggregare le metriche per ente, modalita'
 * di routing e backend di destinazione.
 *
 * @see io.micrometer.core.instrument.MeterRegistry
 * @see EnteCacheService
 */
@Service
public class MiddlewareMetricsService {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareMetricsService.class);

    /** Nome della metrica contatore per le richieste totali. */
    static final String METRICA_RICHIESTE_TOTALI = "middleware.richieste.totali";

    /** Nome della metrica timer per la durata delle richieste. */
    static final String METRICA_RICHIESTE_DURATA = "middleware.richieste.durata";

    /** Nome della metrica gauge per il totale enti censiti. */
    static final String METRICA_ENTI_TOTALI = "middleware.enti.totali";

    /** Nome della metrica gauge per gli enti con PU attiva. */
    static final String METRICA_ENTI_PU = "middleware.enti.piattaforma.unitaria";

    /** Descrizione della metrica contatore. */
    private static final String DESC_RICHIESTE = "Numero totale di richieste SOAP processate dal middleware";

    /** Descrizione della metrica timer. */
    private static final String DESC_DURATA = "Durata delle richieste SOAP processate dal middleware";

    /** Descrizione gauge enti totali. */
    private static final String DESC_ENTI_TOTALI = "Numero totale di enti censiti nella cache (mygov_ente)";

    /** Descrizione gauge enti PU. */
    private static final String DESC_ENTI_PU = "Numero di enti con configurazione PU attiva";

    private final MeterRegistry meterRegistry;

    /**
     * Crea il servizio metriche e registra i gauge per gli enti.
     *
     * @param meterRegistry   registro Micrometer per la pubblicazione delle metriche
     * @param enteCacheService servizio cache per leggere il numero di enti attivi
     */
    public MiddlewareMetricsService(MeterRegistry meterRegistry,
                                    EnteCacheService enteCacheService) {
        this.meterRegistry = meterRegistry;

        // Gauge: numero totale di enti censiti (mygov_ente)
        meterRegistry.gauge(METRICA_ENTI_TOTALI, enteCacheService,
                cache -> (double) cache.size());

        // Gauge: numero di enti con flusso PU attivo
        meterRegistry.gauge(METRICA_ENTI_PU, enteCacheService,
                cache -> (double) cache.countEntiPiattaformaUnitaria());

        log.info("Metriche middleware inizializzate: {}, {}, {}, {}",
                METRICA_RICHIESTE_TOTALI, METRICA_RICHIESTE_DURATA,
                METRICA_ENTI_TOTALI, METRICA_ENTI_PU);
    }

    /**
     * Registra una richiesta SOAP completata con successo.
     *
     * <p>Incrementa il contatore delle richieste con tag {@code esito=OK} e
     * registra la durata nell'istogramma.
     *
     * @param codIpaEnte codice IPA dell'ente
     * @param decision   decisione di routing
     * @param durataMs   durata della transazione in millisecondi
     */
    public void registraSuccesso(String codIpaEnte, RoutingDecision decision, long durataMs) {
        registraMetriche(codIpaEnte, decision, "OK", durataMs);
    }

    /**
     * Registra una richiesta SOAP fallita.
     *
     * <p>Incrementa il contatore delle richieste con tag {@code esito=ERRORE} e
     * registra la durata nell'istogramma.
     *
     * @param codIpaEnte codice IPA dell'ente (puo' essere null se non estratto)
     * @param decision   decisione di routing (puo' essere null se l'errore precede il routing)
     * @param durataMs   durata della transazione in millisecondi
     */
    public void registraErrore(String codIpaEnte, RoutingDecision decision, long durataMs) {
        registraMetriche(codIpaEnte, decision, "ERRORE", durataMs);
    }

    /**
     * Registra le metriche per una richiesta SOAP.
     *
     * <p>Protetto da try-catch: un errore nella raccolta metriche non deve mai
     * interferire con il flusso principale.
     */
    private void registraMetriche(String codIpaEnte, RoutingDecision decision,
                                   String esito, long durataMs) {
        try {
            String ente = codIpaEnte != null ? codIpaEnte : "sconosciuto";
            String modalita = decision != null ? decision.getModalita().name() : "SCONOSCIUTA";
            String destinazione = decision != null ? decision.getDestinazione().name() : "SCONOSCIUTA";

            // Contatore richieste: incrementa con tag
            Counter.builder(METRICA_RICHIESTE_TOTALI)
                    .description(DESC_RICHIESTE)
                    .tag("ente", ente)
                    .tag("modalita", modalita)
                    .tag("destinazione", destinazione)
                    .tag("esito", esito)
                    .register(meterRegistry)
                    .increment();

            // Timer durata: registra la durata in millisecondi
            Timer.builder(METRICA_RICHIESTE_DURATA)
                    .description(DESC_DURATA)
                    .tag("modalita", modalita)
                    .tag("destinazione", destinazione)
                    .register(meterRegistry)
                    .record(durataMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            // Le metriche non devono mai bloccare il flusso principale
            log.warn("Errore nella registrazione delle metriche: {}", e.getMessage());
        }
    }
}
