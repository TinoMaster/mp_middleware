package it.ariaspa.mypay.mypaycore.api.config;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test unitari per {@link BackendRoutingConfig}.
 * <p>
 * Verifica:
 * <ul>
 *   <li>Restituzione corretta degli URL base per ogni backend destinatario</li>
 *   <li>Funzionamento del metodo {@code getBaseUrlFor()}</li>
 * </ul>
 */
class BackendRoutingConfigTest {

    private BackendRoutingConfig config;

    @BeforeEach
    void setUp() {
        config = new BackendRoutingConfig();

        BackendRoutingConfig.BackendProperties mypivotProps = new BackendRoutingConfig.BackendProperties();
        mypivotProps.setBaseUrl("http://localhost:8081");
        config.setMypivot(mypivotProps);

        BackendRoutingConfig.BackendProperties mypayProps = new BackendRoutingConfig.BackendProperties();
        mypayProps.setBaseUrl("http://localhost:8082");
        config.setMypay(mypayProps);
    }

    @Test
    @DisplayName("getBaseUrlFor(MYPIVOT) - restituisce l'URL di mypivot")
    void getBaseUrlFor_mypivot_returnsCorrectUrl() {
        String url = config.getBaseUrlFor(BackendDestinatario.MYPIVOT);
        assertEquals("http://localhost:8081", url);
    }

    @Test
    @DisplayName("getBaseUrlFor(MYPAY) - restituisce l'URL di mypay")
    void getBaseUrlFor_mypay_returnsCorrectUrl() {
        String url = config.getBaseUrlFor(BackendDestinatario.MYPAY);
        assertEquals("http://localhost:8082", url);
    }

    @Test
    @DisplayName("Le proprietà mypivot e mypay sono configurabili via setter")
    void backendProperties_areConfigurable() {
        BackendRoutingConfig.BackendProperties props = new BackendRoutingConfig.BackendProperties();
        props.setBaseUrl("http://custom-host:9090");

        config.setMypay(props);

        assertEquals("http://custom-host:9090", config.getMypay().getBaseUrl());
    }
}
