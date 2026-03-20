package it.ariaspa.mypay.mypaycore.api.health;

import it.ariaspa.mypay.mypaycore.api.repository.EnteConfigCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator che verifica lo stato della configurazione enti nel middleware.
 *
 * <p>Controlla due condizioni:
 * <ol>
 *   <li>La cache delle configurazioni enti e' raggiungibile (proxy per il DB)</li>
 *   <li>Esiste almeno un record attivo nella tabella {@code mwpay_ente_config}</li>
 * </ol>
 *
 * <p>Stati possibili:
 * <ul>
 *   <li><strong>UP</strong>: cache raggiungibile e contiene almeno una configurazione attiva</li>
 *   <li><strong>DOWN</strong>: cache vuota — nessun ente configurato (il middleware
 *       non puo instradare alcuna richiesta)</li>
 *   <li><strong>DOWN con errore</strong>: eccezione durante il controllo (problemi di
 *       connessione al DB)</li>
 * </ul>
 *
 * <p>Questo health check e' registrato automaticamente tramite {@code @Component} e
 * contribuisce all'endpoint {@code /actuator/health}.
 *
 * @see EnteConfigCacheService
 */
@Component
public class EnteConfigHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(EnteConfigHealthIndicator.class);

    private final EnteConfigCacheService enteConfigCacheService;

    /**
     * Crea l'health indicator con il servizio cache iniettato.
     *
     * @param enteConfigCacheService servizio cache per le configurazioni enti
     */
    public EnteConfigHealthIndicator(EnteConfigCacheService enteConfigCacheService) {
        this.enteConfigCacheService = enteConfigCacheService;
    }

    @Override
    public Health health() {
        try {
            int dimensioneCache = enteConfigCacheService.size();

            if (dimensioneCache > 0) {
                return Health.up()
                        .withDetail("stato", "Configurazione enti attiva")
                        .withDetail("entiConfigurati", dimensioneCache)
                        .build();
            } else {
                return Health.down()
                        .withDetail("stato", "Nessun ente configurato — il middleware "
                                + "non puo instradare richieste")
                        .withDetail("entiConfigurati", 0)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Errore durante il controllo health della configurazione enti: {}",
                    e.getMessage());
            return Health.down()
                    .withDetail("stato", "Errore durante il controllo della configurazione enti")
                    .withException(e)
                    .build();
        }
    }
}
