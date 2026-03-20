package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.EnteConfig;
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
 * Servizio di cache in-memory per le configurazioni di routing degli enti.
 *
 * <p>Mantiene una copia in memoria della tabella {@code mwpay_ente_config} con un TTL
 * configurabile. Alla scadenza del TTL, la cache viene ricaricata dal database alla
 * prossima richiesta.
 *
 * <p>La cache e' thread-safe grazie all'uso di {@link ConcurrentHashMap} e {@link ReentrantLock}
 * per il refresh. Solo un thread alla volta puo ricaricare i dati dal DB; gli altri
 * leggono la cache esistente (anche se scaduta) per evitare blocchi.
 *
 * <p>Struttura della cache:
 * <pre>
 * Chiave: "codIpaEnte|tipoOperazione" (es. "R_LOMBARDIA|pivotSILAutorizzaImportFlussoTesoreria")
 * Valore: istanza di {@link EnteConfig}
 * </pre>
 *
 * <p>Proprieta configurabili:
 * <ul>
 *   <li>{@code middleware.cache.ente-config.ttl-seconds} — durata della cache in secondi (default: 300 = 5 minuti)</li>
 * </ul>
 *
 * @see EnteConfigRepository
 * @see EnteConfig
 */
@Service
public class EnteConfigCacheService {

    private static final Logger log = LoggerFactory.getLogger(EnteConfigCacheService.class);

    /** Separatore usato per comporre la chiave della cache. */
    private static final String CACHE_KEY_SEPARATOR = "|";

    /** TTL della cache in secondi, configurabile tramite properties. */
    @Value("${middleware.cache.ente-config.ttl-seconds:300}")
    private long ttlSeconds;

    /** Repository Jdbi per le query al database. */
    private final EnteConfigRepository enteConfigRepository;

    /** Mappa thread-safe: chiave composita -> configurazione ente. */
    private final Map<String, EnteConfig> cache = new ConcurrentHashMap<>();

    /** Timestamp dell'ultimo caricamento della cache. */
    private volatile Instant ultimoCaricamento = Instant.EPOCH;

    /** Lock per il refresh: evita che piu thread ricarichino la cache contemporaneamente. */
    private final ReentrantLock refreshLock = new ReentrantLock();

    /**
     * Crea il servizio di cache con il repository iniettato.
     *
     * @param enteConfigRepository repository Jdbi per l'accesso al DB
     */
    public EnteConfigCacheService(EnteConfigRepository enteConfigRepository) {
        this.enteConfigRepository = enteConfigRepository;
    }

    /**
     * Carica la cache al bootstrap dell'applicazione.
     */
    @PostConstruct
    public void init() {
        log.info("Inizializzazione cache ente-config con TTL di {} secondi", ttlSeconds);
        refreshCache();
    }

    /**
     * Recupera la configurazione di routing per un ente e tipo di operazione.
     *
     * <p>Se la cache e' scaduta, tenta un refresh in background. Se il refresh
     * fallisce, continua a servire i dati dalla cache esistente (stale-while-revalidate).
     *
     * @param codIpaEnte     codice IPA dell'ente
     * @param tipoOperazione tipo di operazione SOAP
     * @return configurazione dell'ente se presente e attiva, vuoto altrimenti
     */
    public Optional<EnteConfig> findByCodIpaEnteAndTipoOperazione(String codIpaEnte, String tipoOperazione) {
        refreshIfExpired();
        String key = buildKey(codIpaEnte, tipoOperazione);
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Verifica se un ente e' censito nel sistema (ha almeno una configurazione attiva).
     *
     * @param codIpaEnte codice IPA dell'ente
     * @return {@code true} se l'ente ha almeno una regola di routing attiva
     */
    public boolean isEnteCensito(String codIpaEnte) {
        refreshIfExpired();
        return cache.keySet().stream()
                .anyMatch(key -> key.startsWith(codIpaEnte + CACHE_KEY_SEPARATOR));
    }

    /**
     * Restituisce il numero di configurazioni attive in cache.
     *
     * @return numero di record in cache
     */
    public int size() {
        return cache.size();
    }

    /**
     * Forza il refresh della cache dal database, indipendentemente dal TTL.
     * Utile per operazioni amministrative o test.
     */
    public void forceRefresh() {
        log.info("Refresh forzato della cache ente-config");
        refreshCache();
    }

    /**
     * Controlla se la cache e' scaduta e, in caso affermativo, avvia il refresh.
     *
     * <p>Il refresh e' protetto da un lock: solo un thread lo esegue,
     * gli altri continuano a usare la cache corrente.
     */
    private void refreshIfExpired() {
        if (isCacheExpired()) {
            if (refreshLock.tryLock()) {
                try {
                    // Ricontrolla dopo aver acquisito il lock (double-check)
                    if (isCacheExpired()) {
                        refreshCache();
                    }
                } finally {
                    refreshLock.unlock();
                }
            } else {
                log.debug("Cache scaduta ma refresh gia in corso da un altro thread — uso cache corrente");
            }
        }
    }

    /**
     * Ricarica tutta la cache dal database.
     *
     * <p>Se la query al DB fallisce, la cache corrente viene mantenuta
     * e l'errore viene registrato nel log applicativo.
     */
    private void refreshCache() {
        try {
            List<EnteConfig> configurazioni = enteConfigRepository.findAllAttive();
            Map<String, EnteConfig> nuovaCache = new ConcurrentHashMap<>();

            for (EnteConfig config : configurazioni) {
                String key = buildKey(config.getCodIpaEnte(), config.getTipoOperazione());
                nuovaCache.put(key, config);
            }

            // Sostituzione atomica del contenuto della cache
            cache.clear();
            cache.putAll(nuovaCache);
            ultimoCaricamento = Instant.now();

            log.info("Cache ente-config aggiornata: {} configurazioni attive caricate", cache.size());
        } catch (Exception e) {
            log.warn("Errore durante il refresh della cache ente-config — mantengo cache corrente "
                    + "(dimensione: {}): {}", cache.size(), e.getMessage());
            // Non rilancia l'eccezione: la cache stale e' meglio di nessuna cache
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

    /**
     * Costruisce la chiave composita per la cache.
     *
     * @param codIpaEnte     codice IPA dell'ente
     * @param tipoOperazione tipo di operazione
     * @return chiave nel formato {@code "codIpaEnte|tipoOperazione"}
     */
    private String buildKey(String codIpaEnte, String tipoOperazione) {
        return codIpaEnte + CACHE_KEY_SEPARATOR + tipoOperazione;
    }
}
