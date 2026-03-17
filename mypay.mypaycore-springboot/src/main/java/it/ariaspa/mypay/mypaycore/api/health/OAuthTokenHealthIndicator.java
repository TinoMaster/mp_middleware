package it.ariaspa.mypay.mypaycore.api.health;

import it.ariaspa.mypay.mypaycore.api.auth.OAuthTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator che verifica lo stato del token OAuth2 verso la Piattaforma Unitaria.
 *
 * Controlla se il token in cache e valido (non scaduto).
 * Non forza un nuovo login se il token e assente - riporta solo lo stato attuale.
 *
 * Stati possibili:
 * - UP: token in cache valido e non scaduto
 * - DOWN: token assente o scaduto (verra rinnovato al prossimo utilizzo)
 * - DOWN con errore: eccezione durante il controllo
 */
@Component
public class OAuthTokenHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenHealthIndicator.class);

    private final OAuthTokenService oAuthTokenService;

    public OAuthTokenHealthIndicator(OAuthTokenService oAuthTokenService) {
        this.oAuthTokenService = oAuthTokenService;
    }

    @Override
    public Health health() {
        try {
            if (oAuthTokenService.isTokenValid()) {
                return Health.up()
                        .withDetail("stato", "Token OAuth2 in cache valido")
                        .build();
            } else {
                return Health.down()
                        .withDetail("stato", "Token OAuth2 assente o scaduto (verra rinnovato al prossimo utilizzo)")
                        .build();
            }
        } catch (Exception e) {
            log.warn("Errore durante il controllo health del token OAuth2: {}", e.getMessage());
            return Health.down()
                    .withDetail("stato", "Errore durante il controllo")
                    .withException(e)
                    .build();
        }
    }
}
