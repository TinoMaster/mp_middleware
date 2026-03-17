package it.ariaspa.mypay.mypaycore.api.health;

import it.ariaspa.mypay.mypaycore.api.config.PiattaformaUnitariaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Health indicator che verifica la raggiungibilita della Piattaforma Unitaria.
 *
 * Effettua un tentativo di connessione leggero (HEAD o GET sulla base URL)
 * per verificare che la piattaforma sia raggiungibile dalla rete.
 *
 * I timeout sono volutamente ridotti per non rallentare il health check.
 *
 * Stati possibili:
 * - UP: la piattaforma e raggiungibile
 * - DOWN: la piattaforma non e raggiungibile (timeout, DNS, rete)
 */
@Component
public class PiattaformaUnitariaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(PiattaformaUnitariaHealthIndicator.class);

    /** Timeout ridotti per il health check (non devono rallentare l'endpoint). */
    private static final int HEALTH_CHECK_CONNECT_TIMEOUT_MS = 3_000;
    private static final int HEALTH_CHECK_READ_TIMEOUT_MS = 5_000;

    private final PiattaformaUnitariaConfig config;
    private final RestTemplate healthCheckRestTemplate;

    public PiattaformaUnitariaHealthIndicator(PiattaformaUnitariaConfig config) {
        this.config = config;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(HEALTH_CHECK_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(HEALTH_CHECK_READ_TIMEOUT_MS);
        this.healthCheckRestTemplate = new RestTemplate(factory);
    }

    @Override
    public Health health() {
        String baseUrl = config.getBaseUrl();
        // Usa /mock/status se siamo in modalità locale, altrimenti la base URL
        String healthUrl = baseUrl.contains("/mock") ? baseUrl + "/status" : baseUrl;

        try {
            // Tentativo leggero di connessione alla piattaforma
            healthCheckRestTemplate.getForEntity(healthUrl, String.class);
            return Health.up()
                    .withDetail("url", baseUrl)
                    .withDetail("stato", "Piattaforma Unitaria raggiungibile")
                    .build();

        } catch (Exception e) {
            log.debug("Health check Piattaforma Unitaria fallito: {}", e.getMessage());
            return Health.down()
                    .withDetail("url", baseUrl)
                    .withDetail("stato", "Piattaforma Unitaria non raggiungibile")
                    .withDetail("errore", e.getMessage())
                    .build();
        }
    }
}
