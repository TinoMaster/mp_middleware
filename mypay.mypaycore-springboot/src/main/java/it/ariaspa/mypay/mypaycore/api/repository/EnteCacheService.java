package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.Ente;
import it.ariaspa.mypay.mypaycore.api.domain.EnteCompleto;
import it.ariaspa.mypay.mypaycore.api.domain.EnteConfigPu;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servizio di cache in-memory per le informazioni degli enti e le loro configurazioni
 * verso la Piattaforma Unitaria.
 *
 * <p>Mantiene due mappe concorrenti:
 * <ul>
 *   <li>{@code codIpaEnte -> EnteCompleto} — lookup primario per codice IPA</li>
 *   <li>{@code codiceFiscaleEnte -> EnteCompleto} — lookup secondario per codice fiscale
 *       (usato dagli endpoint MyPay che identificano l'ente tramite
 *       {@code identificativoDominio})</li>
 * </ul>
 *
 * <p>Entrambe le mappe sono caricate dalla query JOIN tra {@code mygov_ente}
 * e {@code mygov_ente_config_pu}. La cache ha un TTL configurabile; alla scadenza
 * viene ricaricata dal database alla prossima richiesta.
 *
 * <p>La cache e' thread-safe grazie all'uso di {@link ConcurrentHashMap} e
 * {@link ReentrantLock} per il refresh. Solo un thread alla volta puo' ricaricare
 * i dati dal DB; gli altri leggono la cache esistente (stale-while-revalidate).
 *
 * <p>Proprieta' configurabili:
 * <ul>
 *   <li>{@code middleware.cache.ente-config.ttl-seconds} — durata TTL in secondi (default: 300)</li>
 * </ul>
 *
 * @see EnteRepository
 * @see EnteConfigPuRepository
 * @see EnteCompleto
 */
@Service
public class EnteCacheService {

    private static final Logger log = LoggerFactory.getLogger(EnteCacheService.class);

    /** TTL della cache in secondi, configurabile tramite properties. */
    @Value("${middleware.cache.ente-config.ttl-seconds:300}")
    private long ttlSeconds;

    /** DAO per la tabella mygov_ente. */
    private final EnteRepository enteRepository;

    /** DAO per la tabella mygov_ente_config_pu. */
    private final EnteConfigPuRepository enteConfigPuRepository;

    /**
     * Mappa thread-safe primaria: codIpaEnte -> informazioni complete dell'ente.
     * Contiene TUTTI gli enti di mygov_ente, con o senza configurazione PU.
     */
    private final Map<String, EnteCompleto> cacheByCodIpa = new ConcurrentHashMap<>();

    /**
     * Mappa thread-safe secondaria: codiceFiscaleEnte -> informazioni complete dell'ente.
     * Usata per risolvere l'identificativoDominio (codice fiscale) presente negli
     * header SOAP dei servizi MyPay al corrispondente EnteCompleto.
     *
     * <p>Contiene solo gli enti che hanno un codice fiscale non nullo in mygov_ente.
     */
    private final Map<String, EnteCompleto> cacheByCodiceFiscale = new ConcurrentHashMap<>();

    /** Timestamp dell'ultimo caricamento della cache. */
    private volatile Instant ultimoCaricamento = Instant.EPOCH;

    /** Lock per il refresh: evita che piu' thread ricarichino la cache contemporaneamente. */
    private final ReentrantLock refreshLock = new ReentrantLock();

    /**
     * Crea il servizio di cache con i repository iniettati.
     *
     * @param enteRepository          DAO per mygov_ente
     * @param enteConfigPuRepository  DAO per mygov_ente_config_pu
     */
    public EnteCacheService(EnteRepository enteRepository,
                            EnteConfigPuRepository enteConfigPuRepository) {
        this.enteRepository = enteRepository;
        this.enteConfigPuRepository = enteConfigPuRepository;
    }

    /**
     * Carica la cache al bootstrap dell'applicazione.
     */
    @PostConstruct
    public void init() {
        log.info("Inizializzazione cache enti con TTL di {} secondi", ttlSeconds);
        refreshCache();
    }

    /**
     * Cerca un ente per codice IPA, aggiornando la cache se scaduta.
     *
     * <p>Se la cache e' scaduta, tenta un refresh. Se il refresh fallisce,
     * continua a servire i dati dalla cache esistente (stale-while-revalidate).
     *
     * @param codIpaEnte codice IPA dell'ente da cercare
     * @return {@link EnteCompleto} se l'ente e' censito in {@code mygov_ente},
     *         {@link Optional#empty()} se non esiste
     */
    public Optional<EnteCompleto> findByCodIpaEnte(String codIpaEnte) {
        refreshIfExpired();
        return Optional.ofNullable(cacheByCodIpa.get(codIpaEnte));
    }

    /**
     * Cerca un ente per codice fiscale (identificativoDominio), aggiornando la cache se scaduta.
     *
     * <p>Usato dagli endpoint SOAP MyPay che identificano l'ente tramite
     * {@code <identificativoDominio>} nell'header SOAP (CCPPa, Esito, CCP, CCP25,
     * RT, RP, AvvisiDigitali).
     *
     * @param codiceFiscale codice fiscale dell'ente (es. {@code "80007580279"})
     * @return {@link EnteCompleto} se trovato, {@link Optional#empty()} altrimenti
     */
    public Optional<EnteCompleto> findByCodiceFiscale(String codiceFiscale) {
        refreshIfExpired();
        return Optional.ofNullable(cacheByCodiceFiscale.get(codiceFiscale));
    }

    /**
     * Restituisce il numero di enti presenti nella cache (tutti gli enti di mygov_ente).
     *
     * @return numero di enti in cache
     */
    public int size() {
        return cacheByCodIpa.size();
    }

    /**
     * Restituisce il numero di enti con configurazione PU attiva nella cache.
     *
     * @return numero di enti con flusso PU attivo
     */
    public long countEntiPiattaformaUnitaria() {
        return cacheByCodIpa.values().stream()
                .filter(EnteCompleto::isPiattaformaUnitaria)
                .count();
    }

    /**
     * Forza il refresh della cache dal database, indipendentemente dal TTL.
     * Utile per operazioni amministrative.
     */
    public void forceRefresh() {
        log.info("Refresh forzato della cache enti");
        refreshCache();
    }

    /**
     * Controlla se la cache e' scaduta e, in caso affermativo, avvia il refresh.
     * Il refresh e' protetto da lock: solo un thread lo esegue alla volta.
     */
    private void refreshIfExpired() {
        if (isCacheExpired()) {
            if (refreshLock.tryLock()) {
                try {
                    // Double-check dopo aver acquisito il lock
                    if (isCacheExpired()) {
                        refreshCache();
                    }
                } finally {
                    refreshLock.unlock();
                }
            } else {
                log.debug("Cache scaduta ma refresh gia' in corso da un altro thread — uso cache corrente");
            }
        }
    }

    /**
     * Ricarica la cache dal database con una query JOIN tra mygov_ente e mygov_ente_config_pu.
     *
     * <p>Carica prima tutti gli enti, poi tutte le configurazioni PU, e le combina.
     * Popola entrambe le mappe: per codice IPA e per codice fiscale.
     * Se la query al DB fallisce, la cache corrente viene mantenuta.
     */
    private void refreshCache() {
        try {
            // Carica tutti gli enti da mygov_ente
            List<Ente> enti = enteRepository.findAll();

            // Carica tutte le configurazioni PU da mygov_ente_config_pu
            List<EnteConfigPu> configsPu = enteConfigPuRepository.findAll();

            // Crea mappa codiceIpaEnte -> EnteConfigPu per lookup rapido
            Map<String, EnteConfigPu> configPuPerEnte = new ConcurrentHashMap<>();
            for (EnteConfigPu config : configsPu) {
                configPuPerEnte.put(config.getCodiceIpaEnte(), config);
            }

            // Costruisce le nuove cache combinando ente + config PU (opzionale)
            Map<String, EnteCompleto> nuovaCacheByCodIpa = new ConcurrentHashMap<>();
            Map<String, EnteCompleto> nuovaCacheByCodiceFiscale = new ConcurrentHashMap<>();

            for (Ente ente : enti) {
                EnteConfigPu configPu = configPuPerEnte.get(ente.getCodIpaEnte());
                EnteCompleto enteCompleto = new EnteCompleto(ente, configPu);

                // Mappa primaria: per codice IPA
                nuovaCacheByCodIpa.put(ente.getCodIpaEnte(), enteCompleto);

                // Mappa secondaria: per codice fiscale (se disponibile)
                String codiceFiscale = ente.getCodiceFiscaleEnte();
                if (codiceFiscale != null && !codiceFiscale.isBlank()) {
                    nuovaCacheByCodiceFiscale.put(codiceFiscale, enteCompleto);
                }
            }

            // Sostituzione atomica del contenuto delle cache
            cacheByCodIpa.clear();
            cacheByCodIpa.putAll(nuovaCacheByCodIpa);
            cacheByCodiceFiscale.clear();
            cacheByCodiceFiscale.putAll(nuovaCacheByCodiceFiscale);
            ultimoCaricamento = Instant.now();

            long conPu = nuovaCacheByCodIpa.values().stream()
                    .filter(EnteCompleto::isPiattaformaUnitaria).count();
            log.info("Cache enti aggiornata: {} enti totali ({} con PU attiva, {} con flusso legacy), "
                            + "{} enti con codice fiscale indicizzato",
                    cacheByCodIpa.size(), conPu, cacheByCodIpa.size() - conPu,
                    cacheByCodiceFiscale.size());

        } catch (Exception e) {
            log.warn("Errore durante il refresh della cache enti — mantengo cache corrente "
                    + "(dimensione: {}): {}", cacheByCodIpa.size(), e.getMessage());
            // Non rilancia l'eccezione: la cache stale e' preferibile a nessuna cache
        }
    }

    /**
     * Verifica se la cache ha superato il TTL configurato.
     *
     * @return {@code true} se la cache e' scaduta
     */
    private boolean isCacheExpired() {
        return Instant.now().isAfter(ultimoCaricamento.plusSeconds(ttlSeconds));
    }
}
