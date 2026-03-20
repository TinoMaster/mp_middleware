package it.ariaspa.mypay.mypaycore.api.metrics;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.repository.EnteConfigCacheService;
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
 *       processate, con tag: {@code ente}, {@code operazione}, {@code modalita},
 *       {@code destinazione}, {@code esito}</li>
 *   <li><strong>{@code middleware.richieste.durata}</strong> — istogramma della durata
 *       delle richieste in millisecondi, con tag: {@code operazione}, {@code modalita},
 *       {@code destinazione}</li>
 *   <li><strong>{@code middleware.enti.configurati}</strong> — gauge del numero di
 *       configurazioni enti attive nella cache</li>
 * </ul>
 *
 * <p>I tag permettono di filtrare e aggregare le metriche per ente, operazione,
 * modalita di routing e backend di destinazione.
 *
 * @see io.micrometer.core.instrument.MeterRegistry
 * @see EnteConfigCacheService
 */
@Service
public class MiddlewareMetricsService {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareMetricsService.class);

    /** Nome della metrica contatore per le richieste totali. */
    static final String METRICA_RICHIESTE_TOTALI = "middleware.richieste.totali";

    /** Nome della metrica timer per la durata delle richieste. */
    static final String METRICA_RICHIESTE_DURATA = "middleware.richieste.durata";

    /** Nome della metrica gauge per gli enti configurati. */
    static final String METRICA_ENTI_CONFIGURATI = "middleware.enti.configurati";

    /** Descrizione della metrica contatore. */
    private static final String DESC_RICHIESTE = "Numero totale di richieste SOAP processate dal middleware";

    /** Descrizione della metrica timer. */
    private static final String DESC_DURATA = "Durata delle richieste SOAP processate dal middleware";

    /** Descrizione della metrica gauge. */
    private static final String DESC_ENTI = "Numero di configurazioni enti attive nella cache";

    private final MeterRegistry meterRegistry;

    /**
     * Crea il servizio metriche e registra il gauge per gli enti configurati.
     *
     * @param meterRegistry  registro Micrometer per la pubblicazione delle metriche
     * @param enteConfigCacheService servizio cache per leggere il numero di enti attivi
     */
    public MiddlewareMetricsService(MeterRegistry meterRegistry,
                                     EnteConfigCacheService enteConfigCacheService) {
        this.meterRegistry = meterRegistry;

        // Registra il gauge: viene letto periodicamente da Actuator
        meterRegistry.gauge(METRICA_ENTI_CONFIGURATI, enteConfigCacheService,
                cache -> (double) cache.size());

        log.info("Metriche middleware inizializzate: {}, {}, {}",
                METRICA_RICHIESTE_TOTALI, METRICA_RICHIESTE_DURATA, METRICA_ENTI_CONFIGURATI);
    }

    /**
     * Registra una richiesta SOAP completata con successo.
     *
     * <p>Incrementa il contatore delle richieste con tag {@code esito=OK} e
     * registra la durata nell'istogramma.
     *
     * @param codIpaEnte     codice IPA dell'ente
     * @param tipoOperazione tipo di operazione SOAP
     * @param decision       decisione di routing
     * @param durataMs       durata della transazione in millisecondi
     */
    public void registraSuccesso(String codIpaEnte, String tipoOperazione,
                                  RoutingDecision decision, long durataMs) {
        registraMetriche(codIpaEnte, tipoOperazione, decision, "OK", durataMs);
    }

    /**
     * Registra una richiesta SOAP fallita.
     *
     * <p>Incrementa il contatore delle richieste con tag {@code esito=ERRORE} e
     * registra la durata nell'istogramma.
     *
     * @param codIpaEnte     codice IPA dell'ente (puo' essere null se non estratto)
     * @param tipoOperazione tipo di operazione SOAP (puo' essere null)
     * @param decision       decisione di routing (puo' essere null se l'errore precede il routing)
     * @param durataMs       durata della transazione in millisecondi
     */
    public void registraErrore(String codIpaEnte, String tipoOperazione,
                                RoutingDecision decision, long durataMs) {
        registraMetriche(codIpaEnte, tipoOperazione, decision, "ERRORE", durataMs);
    }

    /**
     * Registra le metriche per una richiesta SOAP.
     *
     * <p>Protetto da try-catch: un errore nella raccolta metriche non deve mai
     * interferire con il flusso principale.
     */
    private void registraMetriche(String codIpaEnte, String tipoOperazione,
                                   RoutingDecision decision, String esito, long durataMs) {
        try {
            String ente = codIpaEnte != null ? codIpaEnte : "sconosciuto";
            String operazione = tipoOperazione != null ? tipoOperazione : "sconosciuta";
            String modalita = decision != null ? decision.getModalita().name() : "SCONOSCIUTA";
            String destinazione = decision != null ? decision.getDestinazione().name() : "SCONOSCIUTA";

            // Contatore richieste: incrementa con tag
            Counter.builder(METRICA_RICHIESTE_TOTALI)
                    .description(DESC_RICHIESTE)
                    .tag("ente", ente)
                    .tag("operazione", operazione)
                    .tag("modalita", modalita)
                    .tag("destinazione", destinazione)
                    .tag("esito", esito)
                    .register(meterRegistry)
                    .increment();

            // Timer durata: registra la durata in millisecondi
            Timer.builder(METRICA_RICHIESTE_DURATA)
                    .description(DESC_DURATA)
                    .tag("operazione", operazione)
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
