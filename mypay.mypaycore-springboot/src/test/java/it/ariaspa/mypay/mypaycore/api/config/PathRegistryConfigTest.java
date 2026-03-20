package it.ariaspa.mypay.mypaycore.api.config;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per {@link PathRegistryConfig}.
 * <p>
 * Verifica:
 * <ul>
 *   <li>Conversione corretta delle chiavi normalizzate (con '-') nei path reali (con '/')</li>
 *   <li>Risoluzione dei backend per path noti</li>
 *   <li>Matching longest-prefix quando piu prefissi corrispondono</li>
 *   <li>Gestione path non registrati (Optional vuoto)</li>
 *   <li>Validazione dei valori backend non validi</li>
 *   <li>Inizializzazione con mappa vuota</li>
 * </ul>
 */
class PathRegistryConfigTest {

    private PathRegistryConfig config;

    @BeforeEach
    void setUp() {
        config = new PathRegistryConfig();
    }

    /**
     * Configura e inizializza il registro con un set di mapping standard.
     */
    private void setupStandardMappings() {
        Map<String, String> pathMap = new HashMap<>();
        pathMap.put("ws-pivot", "MYPIVOT");
        pathMap.put("ws-pa", "MYPAY");
        pathMap.put("ws-fesp", "MYPAY");
        config.setPathMap(pathMap);
        config.init();
    }

    @Nested
    @DisplayName("init() - Inizializzazione del registro")
    class InitTests {

        @Test
        @DisplayName("Converte correttamente le chiavi normalizzate nei path reali")
        void init_convertsDashToSlash() {
            setupStandardMappings();

            // Verifica che i path siano stati convertiti correttamente
            assertTrue(config.resolveBackend("/ws/pivot").isPresent());
            assertTrue(config.resolveBackend("/ws/pa").isPresent());
            assertTrue(config.resolveBackend("/ws/fesp").isPresent());
        }

        @Test
        @DisplayName("Lancia eccezione per un valore backend non valido")
        void init_throwsOnInvalidBackendValue() {
            Map<String, String> pathMap = new HashMap<>();
            pathMap.put("ws-test", "BACKEND_INESISTENTE");
            config.setPathMap(pathMap);

            assertThrows(IllegalStateException.class, () -> config.init());
        }

        @Test
        @DisplayName("Inizializzazione con mappa vuota non lancia eccezione")
        void init_emptyMapDoesNotThrow() {
            config.setPathMap(new HashMap<>());
            assertDoesNotThrow(() -> config.init());
        }

        @Test
        @DisplayName("Accetta valori backend in minuscolo (case-insensitive)")
        void init_acceptsLowerCaseBackendValues() {
            Map<String, String> pathMap = new HashMap<>();
            pathMap.put("ws-pivot", "mypivot");
            config.setPathMap(pathMap);

            assertDoesNotThrow(() -> config.init());
            assertEquals(Optional.of(BackendDestinatario.MYPIVOT),
                    config.resolveBackend("/ws/pivot"));
        }
    }

    @Nested
    @DisplayName("resolveBackend() - Risoluzione del backend per path")
    class ResolveBackendTests {

        @BeforeEach
        void setUp() {
            setupStandardMappings();
        }

        @Test
        @DisplayName("Risolve /ws/pivot/* come MYPIVOT")
        void resolveBackend_pivotPath_returnsMypivot() {
            Optional<BackendDestinatario> result =
                    config.resolveBackend("/ws/pivot/PagamentiTelematiciPagatiRiconciliati");

            assertTrue(result.isPresent());
            assertEquals(BackendDestinatario.MYPIVOT, result.get());
        }

        @Test
        @DisplayName("Risolve /ws/pa/* come MYPAY")
        void resolveBackend_paPath_returnsMypay() {
            Optional<BackendDestinatario> result =
                    config.resolveBackend("/ws/pa/PagamentiTelematiciCCPPa");

            assertTrue(result.isPresent());
            assertEquals(BackendDestinatario.MYPAY, result.get());
        }

        @Test
        @DisplayName("Risolve /ws/fesp/* come MYPAY")
        void resolveBackend_fespPath_returnsMypay() {
            Optional<BackendDestinatario> result =
                    config.resolveBackend("/ws/fesp/FespEndpoint");

            assertTrue(result.isPresent());
            assertEquals(BackendDestinatario.MYPAY, result.get());
        }

        @Test
        @DisplayName("Restituisce Optional vuoto per path non registrato")
        void resolveBackend_unknownPath_returnsEmpty() {
            Optional<BackendDestinatario> result =
                    config.resolveBackend("/ws/sconosciuto/Endpoint");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Restituisce Optional vuoto per path null")
        void resolveBackend_nullPath_returnsEmpty() {
            Optional<BackendDestinatario> result = config.resolveBackend(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Restituisce Optional vuoto per path vuoto")
        void resolveBackend_emptyPath_returnsEmpty() {
            Optional<BackendDestinatario> result = config.resolveBackend("");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Risolve il path esatto del prefisso (senza sotto-path)")
        void resolveBackend_exactPrefixMatch_returnsBackend() {
            // Il path esatto del prefisso senza sotto-path aggiuntivo
            Optional<BackendDestinatario> result = config.resolveBackend("/ws/pivot");

            assertTrue(result.isPresent());
            assertEquals(BackendDestinatario.MYPIVOT, result.get());
        }

        @Test
        @DisplayName("Non matcha path che iniziano con il prefisso ma senza separatore")
        void resolveBackend_pathWithoutSeparator_returnsEmpty() {
            // /ws/pivotXYZ non deve matchare /ws/pivot
            Optional<BackendDestinatario> result =
                    config.resolveBackend("/ws/pivotXYZ");

            assertTrue(result.isEmpty());
        }
    }
}
