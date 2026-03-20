package it.ariaspa.mypay.mypaycore.api.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registro configurabile dei path-prefix e il backend associato.
 * <p>
 * Mappa ogni prefisso di path HTTP (es. {@code /ws/pivot}, {@code /ws/pa}, {@code /ws/fesp})
 * al backend di destinazione corrispondente ({@code MYPIVOT} o {@code MYPAY}).
 * <p>
 * Un backend puo gestire piu prefissi. Ad esempio, mypay gestisce sia {@code /ws/pa/*}
 * che {@code /ws/fesp/*}. Il mapping e definito in {@code application.properties} e
 * puo essere esteso senza modificare il codice.
 * <p>
 * Formato delle proprieta:
 * <pre>
 * routing.path-map.ws-pivot=MYPIVOT
 * routing.path-map.ws-pa=MYPAY
 * routing.path-map.ws-fesp=MYPAY
 * </pre>
 * Le chiavi usano {@code -} come separatore al posto di {@code /} perche le chiavi
 * di properties non supportano {@code /}. Il codice converte automaticamente
 * {@code ws-pivot} in {@code /ws/pivot} per il matching.
 * <p>
 * Enum di destinazione: {@link BackendDestinatario}
 *
 * @see BackendRoutingConfig
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "routing")
public class PathRegistryConfig {

    private static final Logger log = LoggerFactory.getLogger(PathRegistryConfig.class);

    /**
     * Separatore usato nelle chiavi di properties (sostituto di '/').
     */
    private static final String PROPERTY_KEY_SEPARATOR = "-";

    /**
     * Separatore reale nei path HTTP.
     */
    private static final String PATH_SEPARATOR = "/";

    /**
     * Mappa path-prefix normalizzato -> nome backend.
     * <p>
     * Le chiavi sono nel formato {@code ws-pivot} (con '-' al posto di '/').
     * I valori devono corrispondere a un valore dell'enum {@link BackendDestinatario}.
     * <p>
     * Esempio:
     * <pre>
     * ws-pivot = MYPIVOT
     * ws-pa    = MYPAY
     * ws-fesp  = MYPAY
     * </pre>
     */
    private Map<String, String> pathMap = new HashMap<>();

    /**
     * Mappa interna con i path reali (con '/') come chiavi per il matching a runtime.
     * Popolata dal metodo {@link #init()}.
     */
    private Map<String, BackendDestinatario> resolvedPathMap = new HashMap<>();

    /**
     * Inizializza la mappa interna convertendo le chiavi normalizzate nei path reali
     * e validando che ogni valore corrisponda a un {@link BackendDestinatario} valido.
     */
    @PostConstruct
    public void init() {
        resolvedPathMap.clear();
        for (Map.Entry<String, String> entry : pathMap.entrySet()) {
            String normalizedKey = entry.getKey();
            String backendName = entry.getValue().trim().toUpperCase();

            // Converte la chiave normalizzata nel path reale: ws-pivot -> /ws/pivot
            String realPath = PATH_SEPARATOR + normalizedKey.replace(PROPERTY_KEY_SEPARATOR, PATH_SEPARATOR);

            try {
                BackendDestinatario backend = BackendDestinatario.valueOf(backendName);
                resolvedPathMap.put(realPath, backend);
                log.info("Registrato path-prefix: {} -> {}", realPath, backend);
            } catch (IllegalArgumentException e) {
                log.error("Valore backend non valido per il path '{}': '{}'. "
                        + "Valori ammessi: {}", realPath, backendName, BackendDestinatario.values());
                throw new IllegalStateException(
                        "Configurazione routing.path-map non valida: backend '" + backendName
                        + "' non riconosciuto per il path '" + realPath + "'");
            }
        }

        if (resolvedPathMap.isEmpty()) {
            log.warn("Nessun path-prefix configurato in routing.path-map. "
                    + "Tutte le richieste genereranno PATH_NON_RICONOSCIUTO.");
        } else {
            log.info("PathRegistryConfig inizializzato con {} path-prefix: {}",
                    resolvedPathMap.size(), resolvedPathMap);
        }
    }

    /**
     * Determina il backend di destinazione in base al path della richiesta HTTP.
     * <p>
     * Cerca un match tra il path della richiesta e i prefissi configurati.
     * Il matching e basato su {@code startsWith}: se il path della richiesta inizia
     * con uno dei prefissi registrati, il backend associato viene restituito.
     * <p>
     * Se piu prefissi corrispondono, viene scelto quello piu lungo (piu specifico).
     *
     * @param requestPath il path della richiesta HTTP (es. {@code /ws/pivot/PagamentiTelematici...})
     * @return un {@link Optional} contenente il backend di destinazione, o vuoto se nessun
     *         prefisso corrisponde
     */
    public Optional<BackendDestinatario> resolveBackend(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return Optional.empty();
        }

        BackendDestinatario bestMatch = null;
        int longestPrefix = 0;

        for (Map.Entry<String, BackendDestinatario> entry : resolvedPathMap.entrySet()) {
            String prefix = entry.getKey();
            // Il path deve iniziare con il prefisso e proseguire con '/' o terminare esattamente
            if (requestPath.startsWith(prefix)
                    && (requestPath.length() == prefix.length()
                        || requestPath.charAt(prefix.length()) == '/')) {
                if (prefix.length() > longestPrefix) {
                    longestPrefix = prefix.length();
                    bestMatch = entry.getValue();
                }
            }
        }

        if (bestMatch != null) {
            log.debug("Path '{}' risolto al backend: {}", requestPath, bestMatch);
        } else {
            log.warn("Nessun backend trovato per il path: {}", requestPath);
        }

        return Optional.ofNullable(bestMatch);
    }

    /**
     * Enum che rappresenta i backend di destinazione supportati dal middleware.
     * <p>
     * Ogni valore corrisponde a una piattaforma backend verso cui il middleware
     * puo instradare le richieste SOAP.
     */
    public enum BackendDestinatario {

        /** Backend mypay: gestisce pagamenti (/ws/pa/*, /ws/fesp/*) */
        MYPAY,

        /** Backend mypivot: gestisce riconciliazione (/ws/pivot/*) */
        MYPIVOT
    }
}
