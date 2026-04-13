package it.ariaspa.mypay.mypaycore.api.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servizio di cache in memoria per le entry del proxy upload.
 *
 * <p>Salva le associazioni {@code authorizationToken → UploadProxyEntry}
 * generate durante il post-processing della risposta di
 * {@code paaSILAutorizzaImportFlusso}. Quando il SIL chiama l'endpoint
 * di upload del middleware, l'entry viene recuperata e rimossa dalla cache.
 *
 * <p>Caratteristiche:
 * <ul>
 *   <li>Thread-safe tramite {@link ConcurrentHashMap}</li>
 *   <li>TTL configurabile per ogni entry</li>
 *   <li>Pulizia periodica delle entry scadute tramite {@code @Scheduled}</li>
 *   <li>Rimozione automatica dopo il primo utilizzo (one-shot)</li>
 * </ul>
 *
 * @see UploadProxyEntry
 */
@Service
public class UploadProxyCacheService {

    private static final Logger log = LoggerFactory.getLogger(UploadProxyCacheService.class);

    /**
     * Cache: authorizationToken → UploadProxyEntry.
     * L'authorizationToken è un JWT generato dal backend (univoco per richiesta).
     */
    private final ConcurrentHashMap<String, UploadProxyEntry> cache = new ConcurrentHashMap<>();

    /**
     * TTL in secondi per le entry nella cache.
     * Default: 3600 secondi (1 ora).
     */
    private final long ttlSecondi;

    public UploadProxyCacheService(
            @Value("${middleware.upload.proxy.cache-ttl-seconds:3600}") long ttlSecondi) {
        this.ttlSecondi = ttlSecondi;
        log.info("UploadProxyCacheService inizializzato con TTL: {} secondi", ttlSecondi);
    }

    /**
     * Salva un'entry nella cache.
     *
     * @param authorizationToken chiave di lookup (JWT generato dal backend)
     * @param entry              i dati dell'upload proxy da salvare
     */
    public void salva(String authorizationToken, UploadProxyEntry entry) {
        cache.put(authorizationToken, entry);
        log.info("Entry salvata nella cache upload proxy per ente '{}', requestToken '{}'",
                entry.getCodIpaEnte(), entry.getRequestToken());
        log.debug("Dimensione cache upload proxy: {}", cache.size());
    }

    /**
     * Recupera e rimuove un'entry dalla cache (one-shot).
     *
     * <p>L'entry viene rimossa dopo il primo recupero per evitare
     * riutilizzi dello stesso authorizationToken.
     *
     * @param authorizationToken chiave di lookup (JWT del backend)
     * @return l'entry se presente e non scaduta, {@code Optional.empty()} altrimenti
     */
    public Optional<UploadProxyEntry> recuperaERimuovi(String authorizationToken) {
        UploadProxyEntry entry = cache.remove(authorizationToken);

        if (entry == null) {
            log.warn("Nessuna entry trovata nella cache upload proxy per l'authorizationToken fornito");
            return Optional.empty();
        }

        if (entry.isScaduta(ttlSecondi)) {
            log.warn("Entry scaduta nella cache upload proxy per ente '{}', requestToken '{}'",
                    entry.getCodIpaEnte(), entry.getRequestToken());
            return Optional.empty();
        }

        log.info("Entry recuperata dalla cache upload proxy per ente '{}', requestToken '{}'",
                entry.getCodIpaEnte(), entry.getRequestToken());
        return Optional.of(entry);
    }

    /**
     * Pulizia periodica delle entry scadute dalla cache.
     * Eseguita ogni 5 minuti per evitare memory leak.
     */
    @Scheduled(fixedDelayString = "${middleware.upload.proxy.cleanup-interval-ms:300000}")
    public void puliziaEntryScadute() {
        int primaDellaPulizia = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isScaduta(ttlSecondi));
        int rimosse = primaDellaPulizia - cache.size();
        if (rimosse > 0) {
            log.info("Pulizia cache upload proxy: {} entry scadute rimosse su {} totali",
                    rimosse, primaDellaPulizia);
        }
    }

    /**
     * Restituisce il numero di entry nella cache (per health check e metriche).
     *
     * @return numero di entry presenti
     */
    public int dimensioneCache() {
        return cache.size();
    }
}
