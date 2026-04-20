package it.ariaspa.mypay.mypaycore.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione globale per la connessione alla Piattaforma Unitaria (pagoPA).
 *
 * <p>Legge le proprieta' dal blocco {@code piattaforma-unitaria} in application.properties.
 * Contiene l'URL di base e i parametri OAuth2 globali (token URL, grant type, scope).
 *
 * <p>Le credenziali per-ente ({@code client_id} e {@code client_secret}) <strong>non</strong>
 * sono piu' presenti qui: vengono lette dalla tabella {@code mygov_ente_config_pu} tramite
 * {@code EnteCacheService} e passate a {@code OAuthTokenService} a runtime.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "piattaforma-unitaria")
public class PiattaformaUnitariaConfig {

    /**
     * URL base della Piattaforma Unitaria (es. https://api.uat.p4pa.pagopa.it).
     */
    private String baseUrl;

    /**
     * Configurazione OAuth2 globale per l'autenticazione verso la piattaforma.
     */
    private Auth auth = new Auth();

    /**
     * Parametri OAuth2 globali (comuni a tutti gli enti).
     * Le credenziali per-ente (client_id, client_secret) sono nel DB.
     */
    @Getter
    @Setter
    public static class Auth {

        /**
         * URL dell'endpoint di autenticazione OAuth2.
         * Es. https://api.uat.p4pa.pagopa.it/pu/auth/oauth/token
         */
        private String tokenUrl;

        /**
         * Grant type OAuth2 (default: client_credentials).
         */
        private String grantType = "client_credentials";

        /**
         * Scope OAuth2 (default: openid).
         */
        private String scope = "openid";
    }
}
