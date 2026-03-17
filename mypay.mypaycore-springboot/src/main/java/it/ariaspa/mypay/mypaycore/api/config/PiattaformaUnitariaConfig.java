package it.ariaspa.mypay.mypaycore.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione per la connessione alla Piattaforma Unitaria (pagoPA).
 * <p>
 * Legge le proprietà dal blocco 'piattaforma-unitaria' in application.yml.
 * Contiene le URL di base e le credenziali OAuth2 per l'autenticazione
 * tramite Client Credentials Flow.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "piattaforma-unitaria")
public class PiattaformaUnitariaConfig {

    /**
     * URL base della Piattaforma Unitaria (es. https://api.uat.p4pa.pagopa.it)
     */
    private String baseUrl;

    /**
     * Configurazione OAuth2 per l'autenticazione verso la piattaforma.
     */
    private Auth auth = new Auth();

    /**
     * Configurazione dei parametri OAuth2 Client Credentials.
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
         * Client ID per l'autenticazione OAuth2.
         */
        private String clientId;

        /**
         * Client Secret per l'autenticazione OAuth2.
         */
        private String clientSecret;

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
