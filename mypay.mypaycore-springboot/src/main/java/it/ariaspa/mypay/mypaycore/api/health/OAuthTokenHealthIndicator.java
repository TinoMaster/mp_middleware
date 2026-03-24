package it.ariaspa.mypay.mypaycore.api.health;

import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Health indicator che verifica lo stato della cache dei token OAuth2 per tutti gli enti.
 *
 * <p>Controlla quanti enti hanno un token valido in cache. Non forza nuovi login —
 * riporta solo lo stato attuale della cache token.
 *
 * <p>Stati possibili:
 * <ul>
 *   <li><strong>UP</strong>: almeno un ente ha un token valido in cache</li>
 *   <li><strong>DOWN</strong>: nessun token in cache (tutti scaduti o mai richiesti;
 *       i token vengono ottenuti lazily al primo utilizzo)</li>
 *   <li><strong>DOWN con errore</strong>: eccezione durante il controllo</li>
 * </ul>
 *
 * <p>Il fatto che la cache sia vuota non indica necessariamente un problema:
 * i token vengono richiesti lazily alla prima richiesta per ciascun ente.
 */
@Component
public class OAuthTokenHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenHealthIndicator.class);

    private final OAuthTokenService oAuthTokenService;

    /**
     * Crea l'health indicator con il servizio token iniettato.
     *
     * @param oAuthTokenService servizio per la gestione dei token OAuth2 per-ente
     */
    public OAuthTokenHealthIndicator(OAuthTokenService oAuthTokenService) {
        this.oAuthTokenService = oAuthTokenService;
    }

    @Override
    public Health health() {
        try {
            Set<String> entiInCache = oAuthTokenService.getEntiInCache();
            int totaleInCache = oAuthTokenService.getTokenCacheSize();

            // Conta quanti enti hanno un token ancora valido
            long tokensValidi = entiInCache.stream()
                    .filter(oAuthTokenService::isTokenValid)
                    .count();

            if (tokensValidi > 0) {
                return Health.up()
                        .withDetail("stato", "Token OAuth2 in cache")
                        .withDetail("tokensValidi", tokensValidi)
                        .withDetail("tokensInCache", totaleInCache)
                        .build();
            } else {
                return Health.down()
                        .withDetail("stato", "Nessun token OAuth2 valido in cache "
                                + "(i token vengono ottenuti lazily al primo utilizzo per ogni ente)")
                        .withDetail("tokensInCache", totaleInCache)
                        .build();
            }

        } catch (Exception e) {
            log.warn("Errore durante il controllo health dei token OAuth2: {}", e.getMessage());
            return Health.down()
                    .withDetail("stato", "Errore durante il controllo")
                    .withException(e)
                    .build();
        }
    }
}
