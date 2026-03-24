package it.ariaspa.mypay.mypaycore.api.health;

import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator che verifica lo stato della cache degli enti nel middleware.
 *
 * <p>Controlla due condizioni:
 * <ol>
 *   <li>La cache degli enti e' raggiungibile (proxy per il DB)</li>
 *   <li>Esiste almeno un ente censito in {@code mygov_ente}</li>
 * </ol>
 *
 * <p>Stati possibili:
 * <ul>
 *   <li><strong>UP</strong>: cache raggiungibile e contiene almeno un ente</li>
 *   <li><strong>DOWN</strong>: cache vuota — nessun ente configurato (il middleware
 *       non puo' instradare alcuna richiesta)</li>
 *   <li><strong>DOWN con errore</strong>: eccezione durante il controllo (problemi di
 *       connessione al DB)</li>
 * </ul>
 *
 * <p>Espone anche il numero di enti con configurazione PU attiva rispetto al totale.
 *
 * @see EnteCacheService
 */
@Component
public class EnteConfigHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(EnteConfigHealthIndicator.class);

    private final EnteCacheService enteCacheService;

    /**
     * Crea l'health indicator con il servizio cache enti iniettato.
     *
     * @param enteCacheService servizio cache per gli enti ({@code mygov_ente} + {@code mygov_ente_config_pu})
     */
    public EnteConfigHealthIndicator(EnteCacheService enteCacheService) {
        this.enteCacheService = enteCacheService;
    }

    @Override
    public Health health() {
        try {
            int totaleEnti = enteCacheService.size();
            long entiConPu = enteCacheService.countEntiPiattaformaUnitaria();

            if (totaleEnti > 0) {
                return Health.up()
                        .withDetail("stato", "Cache enti attiva")
                        .withDetail("entiTotali", totaleEnti)
                        .withDetail("entiConPiattaformaUnitaria", entiConPu)
                        .withDetail("entiLegacy", totaleEnti - entiConPu)
                        .build();
            } else {
                return Health.down()
                        .withDetail("stato", "Nessun ente in cache — il middleware "
                                + "non puo' instradare richieste")
                        .withDetail("entiTotali", 0)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Errore durante il controllo health della cache enti: {}",
                    e.getMessage());
            return Health.down()
                    .withDetail("stato", "Errore durante il controllo della cache enti")
                    .withException(e)
                    .build();
        }
    }
}
