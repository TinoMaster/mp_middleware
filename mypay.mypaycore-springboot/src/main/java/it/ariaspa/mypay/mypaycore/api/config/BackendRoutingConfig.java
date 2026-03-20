package it.ariaspa.mypay.mypaycore.api.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione degli URL base dei backend legacy (mypay e mypivot).
 * <p>
 * Questa classe carica le proprietà {@code backend.mypivot.base-url} e
 * {@code backend.mypay.base-url} dal file {@code application.properties}.
 * Gli URL sono placeholder configurabili tramite variabili d'ambiente,
 * pensati per essere definiti in fase di deploy.
 * <p>
 * Esempio di proprietà:
 * <pre>
 * backend.mypivot.base-url=${BACKEND_MYPIVOT_URL:http://localhost:8081}
 * backend.mypay.base-url=${BACKEND_MYPAY_URL:http://localhost:8082}
 * </pre>
 * <p>
 * Utilizzata dal {@link it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient}
 * per determinare l'URL di destinazione in base al
 * {@link PathRegistryConfig.BackendDestinatario}.
 *
 * @see PathRegistryConfig
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "backend")
public class BackendRoutingConfig {

    private static final Logger log = LoggerFactory.getLogger(BackendRoutingConfig.class);

    /**
     * Configurazione del backend mypivot (riconciliazione).
     */
    private BackendProperties mypivot = new BackendProperties();

    /**
     * Configurazione del backend mypay (pagamenti).
     */
    private BackendProperties mypay = new BackendProperties();

    /**
     * Logga la configurazione dei backend all'avvio per verifica.
     */
    @PostConstruct
    public void init() {
        log.info("BackendRoutingConfig inizializzato:");
        log.info("  MYPIVOT base-url: {}", mypivot.getBaseUrl());
        log.info("  MYPAY   base-url: {}", mypay.getBaseUrl());
    }

    /**
     * Restituisce l'URL base del backend corrispondente al destinatario specificato.
     *
     * @param destinatario il backend di destinazione (MYPAY o MYPIVOT)
     * @return l'URL base del backend
     * @throws IllegalArgumentException se il destinatario non e supportato
     */
    public String getBaseUrlFor(PathRegistryConfig.BackendDestinatario destinatario) {
        return switch (destinatario) {
            case MYPAY -> mypay.getBaseUrl();
            case MYPIVOT -> mypivot.getBaseUrl();
        };
    }

    /**
     * Proprietà di configurazione per un singolo backend.
     */
    @Getter
    @Setter
    public static class BackendProperties {

        /**
         * URL base del backend (es. {@code http://localhost:8081}).
         * Configurabile tramite variabile d'ambiente.
         */
        private String baseUrl;
    }
}
