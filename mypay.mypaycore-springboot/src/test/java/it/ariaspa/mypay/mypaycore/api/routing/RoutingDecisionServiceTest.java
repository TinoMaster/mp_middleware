package it.ariaspa.mypay.mypaycore.api.routing;

import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PathNonRiconosciutoException;
import it.ariaspa.mypay.mypaycore.api.config.BackendRoutingConfig;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.EnteConfig;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.repository.EnteConfigCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per il {@link RoutingDecisionService}.
 *
 * <p>Verifica l'algoritmo di decisione del routing a due dimensioni:
 * <ol>
 *   <li>Routing per path → destinazione backend (MYPAY/MYPIVOT)</li>
 *   <li>Routing per modalita → PU (OAuth2) o legacy (forward diretto)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RoutingDecisionServiceTest {

    @Mock
    private PathRegistryConfig pathRegistryConfig;

    @Mock
    private EnteConfigCacheService enteConfigCacheService;

    @Mock
    private BackendRoutingConfig backendRoutingConfig;

    private RoutingDecisionService service;

    /** Configurazione di esempio per un ente sulla PU. */
    private static final EnteConfig ENTE_PU = new EnteConfig(
            1L, "R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria",
            ModalitaRouting.PIATTAFORMA_UNITARIA, true, "Migrato alla PU",
            LocalDateTime.now(), LocalDateTime.now()
    );

    /** Configurazione di esempio per un ente legacy. */
    private static final EnteConfig ENTE_LEGACY = new EnteConfig(
            2L, "COMUNE_MILANO", "pivotSILAutorizzaImportFlussoTesoreria",
            ModalitaRouting.LEGACY, true, "Ancora su legacy",
            LocalDateTime.now(), LocalDateTime.now()
    );

    @BeforeEach
    void setUp() {
        service = new RoutingDecisionService(pathRegistryConfig, enteConfigCacheService, backendRoutingConfig);
    }

    @Nested
    @DisplayName("Routing verso Piattaforma Unitaria")
    class RoutingPiattaformaUnitaria {

        @Test
        @DisplayName("Ente migrato alla PU — routing verso PU con OAuth2")
        void decide_enteMigratoPU_restituisceDecisionePU() {
            when(pathRegistryConfig.resolveBackend("/ws/pivot/PagamentiTelematici"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPIVOT));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria"))
                    .thenReturn(Optional.of(ENTE_PU));
            when(backendRoutingConfig.getBaseUrlFor(BackendDestinatario.MYPIVOT))
                    .thenReturn("http://localhost:8081");

            RoutingDecision decision = service.decide(
                    "R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria",
                    "/ws/pivot/PagamentiTelematici");

            assertNotNull(decision);
            assertEquals(BackendDestinatario.MYPIVOT, decision.getDestinazione());
            assertEquals(ModalitaRouting.PIATTAFORMA_UNITARIA, decision.getModalita());
            assertEquals("http://localhost:8081", decision.getUrlBackend());
            assertTrue(decision.isPiattaformaUnitaria());
            assertFalse(decision.isLegacy());
        }

        @Test
        @DisplayName("Ente PU su path mypay — routing corretto")
        void decide_entePU_pathMypay_routingCorretto() {
            EnteConfig entePuMypay = new EnteConfig(
                    3L, "R_LOMBARDIA", "paVerificaRPT",
                    ModalitaRouting.PIATTAFORMA_UNITARIA, true, null,
                    LocalDateTime.now(), LocalDateTime.now()
            );
            when(pathRegistryConfig.resolveBackend("/ws/pa/PagamentiTelematiciCCPPa"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPAY));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("R_LOMBARDIA", "paVerificaRPT"))
                    .thenReturn(Optional.of(entePuMypay));
            when(backendRoutingConfig.getBaseUrlFor(BackendDestinatario.MYPAY))
                    .thenReturn("http://localhost:8082");

            RoutingDecision decision = service.decide(
                    "R_LOMBARDIA", "paVerificaRPT", "/ws/pa/PagamentiTelematiciCCPPa");

            assertEquals(BackendDestinatario.MYPAY, decision.getDestinazione());
            assertTrue(decision.isPiattaformaUnitaria());
        }
    }

    @Nested
    @DisplayName("Routing verso backend legacy")
    class RoutingLegacy {

        @Test
        @DisplayName("Ente legacy — forward diretto verso mypivot")
        void decide_enteLegacy_restituisceDecisioneLegacy() {
            when(pathRegistryConfig.resolveBackend("/ws/pivot/PagamentiTelematici"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPIVOT));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("COMUNE_MILANO", "pivotSILAutorizzaImportFlussoTesoreria"))
                    .thenReturn(Optional.of(ENTE_LEGACY));
            when(backendRoutingConfig.getBaseUrlFor(BackendDestinatario.MYPIVOT))
                    .thenReturn("http://localhost:8081");

            RoutingDecision decision = service.decide(
                    "COMUNE_MILANO", "pivotSILAutorizzaImportFlussoTesoreria",
                    "/ws/pivot/PagamentiTelematici");

            assertNotNull(decision);
            assertEquals(BackendDestinatario.MYPIVOT, decision.getDestinazione());
            assertEquals(ModalitaRouting.LEGACY, decision.getModalita());
            assertEquals("http://localhost:8081", decision.getUrlBackend());
            assertTrue(decision.isLegacy());
            assertFalse(decision.isPiattaformaUnitaria());
        }

        @Test
        @DisplayName("Ente legacy su path fesp — routing corretto")
        void decide_enteLegacy_pathFesp_routingCorretto() {
            EnteConfig enteLegacyFesp = new EnteConfig(
                    4L, "COMUNE_BERGAMO", "nodoSILInviaCarrelloRPT",
                    ModalitaRouting.LEGACY, true, null,
                    LocalDateTime.now(), LocalDateTime.now()
            );
            when(pathRegistryConfig.resolveBackend("/ws/fesp/FespService"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPAY));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("COMUNE_BERGAMO", "nodoSILInviaCarrelloRPT"))
                    .thenReturn(Optional.of(enteLegacyFesp));
            when(backendRoutingConfig.getBaseUrlFor(BackendDestinatario.MYPAY))
                    .thenReturn("http://localhost:8082");

            RoutingDecision decision = service.decide(
                    "COMUNE_BERGAMO", "nodoSILInviaCarrelloRPT", "/ws/fesp/FespService");

            assertEquals(BackendDestinatario.MYPAY, decision.getDestinazione());
            assertTrue(decision.isLegacy());
        }
    }

    @Nested
    @DisplayName("Errori di routing")
    class ErroriRouting {

        @Test
        @DisplayName("Path non riconosciuto → PathNonRiconosciutoException")
        void decide_pathNonRiconosciuto_lanciaEccezione() {
            when(pathRegistryConfig.resolveBackend("/ws/sconosciuto/Servizio"))
                    .thenReturn(Optional.empty());

            PathNonRiconosciutoException ex = assertThrows(PathNonRiconosciutoException.class,
                    () -> service.decide("R_LOMBARDIA", "operazione", "/ws/sconosciuto/Servizio"));

            assertEquals("/ws/sconosciuto/Servizio", ex.getRequestPath());
            assertTrue(ex.getMessage().contains("Path non riconosciuto"));

            // Non deve interrogare il DB se il path non e' valido
            verifyNoInteractions(enteConfigCacheService);
        }

        @Test
        @DisplayName("Ente non censito → EnteNonCensitoException")
        void decide_enteNonCensito_lanciaEccezione() {
            when(pathRegistryConfig.resolveBackend("/ws/pivot/PagamentiTelematici"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPIVOT));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("ENTE_INESISTENTE", "pivotSILAutorizzaImportFlussoTesoreria"))
                    .thenReturn(Optional.empty());

            EnteNonCensitoException ex = assertThrows(EnteNonCensitoException.class,
                    () -> service.decide("ENTE_INESISTENTE", "pivotSILAutorizzaImportFlussoTesoreria",
                            "/ws/pivot/PagamentiTelematici"));

            assertEquals("ENTE_INESISTENTE", ex.getCodIpaEnte());
            assertEquals("pivotSILAutorizzaImportFlussoTesoreria", ex.getTipoOperazione());
            assertTrue(ex.getMessage().contains("Ente non censito"));
        }

        @Test
        @DisplayName("Ente esistente ma operazione non configurata → EnteNonCensitoException")
        void decide_enteEsistenteOperazioneNonConfigurata_lanciaEccezione() {
            when(pathRegistryConfig.resolveBackend("/ws/pivot/PagamentiTelematici"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPIVOT));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("R_LOMBARDIA", "operazioneNonConfigurata"))
                    .thenReturn(Optional.empty());

            EnteNonCensitoException ex = assertThrows(EnteNonCensitoException.class,
                    () -> service.decide("R_LOMBARDIA", "operazioneNonConfigurata",
                            "/ws/pivot/PagamentiTelematici"));

            assertEquals("R_LOMBARDIA", ex.getCodIpaEnte());
            assertEquals("operazioneNonConfigurata", ex.getTipoOperazione());
        }
    }

    @Nested
    @DisplayName("Interazione con le dipendenze")
    class InterazioneDipendenze {

        @Test
        @DisplayName("Verifica ordine delle chiamate: prima path, poi DB")
        void decide_verificaOrdineChiamate() {
            when(pathRegistryConfig.resolveBackend("/ws/pivot/Servizio"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPIVOT));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("R_LOMBARDIA", "operazione"))
                    .thenReturn(Optional.of(ENTE_PU));
            when(backendRoutingConfig.getBaseUrlFor(BackendDestinatario.MYPIVOT))
                    .thenReturn("http://localhost:8081");

            service.decide("R_LOMBARDIA", "operazione", "/ws/pivot/Servizio");

            // Verifica che tutte le dipendenze siano state interrogate
            verify(pathRegistryConfig).resolveBackend("/ws/pivot/Servizio");
            verify(enteConfigCacheService).findByCodIpaEnteAndTipoOperazione("R_LOMBARDIA", "operazione");
            verify(backendRoutingConfig).getBaseUrlFor(BackendDestinatario.MYPIVOT);
        }

        @Test
        @DisplayName("Se path fallisce, non interroga DB ne backendRoutingConfig")
        void decide_pathFallisce_nonInterrogaDB() {
            when(pathRegistryConfig.resolveBackend("/ws/invalido"))
                    .thenReturn(Optional.empty());

            assertThrows(PathNonRiconosciutoException.class,
                    () -> service.decide("R_LOMBARDIA", "operazione", "/ws/invalido"));

            verifyNoInteractions(enteConfigCacheService);
            verifyNoInteractions(backendRoutingConfig);
        }

        @Test
        @DisplayName("Se DB fallisce, non interroga backendRoutingConfig")
        void decide_dbFallisce_nonInterrogaBackendConfig() {
            when(pathRegistryConfig.resolveBackend("/ws/pivot/Servizio"))
                    .thenReturn(Optional.of(BackendDestinatario.MYPIVOT));
            when(enteConfigCacheService.findByCodIpaEnteAndTipoOperazione("ENTE_X", "operazione"))
                    .thenReturn(Optional.empty());

            assertThrows(EnteNonCensitoException.class,
                    () -> service.decide("ENTE_X", "operazione", "/ws/pivot/Servizio"));

            verifyNoInteractions(backendRoutingConfig);
        }
    }

    @Nested
    @DisplayName("RoutingDecision - oggetto risultato")
    class RoutingDecisionTest {

        @Test
        @DisplayName("isPiattaformaUnitaria restituisce true per modalita PU")
        void isPiattaformaUnitaria_modalitaPU_restituisceTrue() {
            RoutingDecision decision = new RoutingDecision(
                    BackendDestinatario.MYPIVOT, ModalitaRouting.PIATTAFORMA_UNITARIA, "http://localhost:8081");
            assertTrue(decision.isPiattaformaUnitaria());
            assertFalse(decision.isLegacy());
        }

        @Test
        @DisplayName("isLegacy restituisce true per modalita LEGACY")
        void isLegacy_modalitaLegacy_restituisceTrue() {
            RoutingDecision decision = new RoutingDecision(
                    BackendDestinatario.MYPAY, ModalitaRouting.LEGACY, "http://localhost:8082");
            assertTrue(decision.isLegacy());
            assertFalse(decision.isPiattaformaUnitaria());
        }

        @Test
        @DisplayName("toString contiene tutte le informazioni")
        void toString_contieneInformazioni() {
            RoutingDecision decision = new RoutingDecision(
                    BackendDestinatario.MYPIVOT, ModalitaRouting.PIATTAFORMA_UNITARIA, "http://localhost:8081");
            String s = decision.toString();
            assertTrue(s.contains("MYPIVOT"));
            assertTrue(s.contains("PIATTAFORMA_UNITARIA"));
            assertTrue(s.contains("http://localhost:8081"));
        }
    }
}
