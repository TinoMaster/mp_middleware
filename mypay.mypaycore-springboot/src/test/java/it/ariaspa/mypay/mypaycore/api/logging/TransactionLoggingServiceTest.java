package it.ariaspa.mypay.mypaycore.api.logging;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.repository.TransactionLogRepository;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test unitari per {@link TransactionLoggingService}.
 *
 * <p>Verifica:
 * <ul>
 *   <li>Logging di transazioni completate con successo</li>
 *   <li>Logging di transazioni fallite con messaggio di errore</li>
 *   <li>Logging di errori pre-routing (senza RoutingDecision)</li>
 *   <li>Troncamento del messaggio di errore oltre la soglia massima</li>
 *   <li>Resilienza: errore DB non propaga eccezione (non blocca la risposta al SIL)</li>
 *   <li>Gestione corretta di parametri null</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TransactionLoggingServiceTest {

    @Mock
    private TransactionLogRepository transactionLogRepository;

    private TransactionLoggingService service;

    /** Decisione di routing verso la PU */
    private static final RoutingDecision DECISION_PU = new RoutingDecision(
            BackendDestinatario.MYPIVOT, ModalitaRouting.PIATTAFORMA_UNITARIA, "http://localhost:8081");

    /** Decisione di routing verso il backend legacy */
    private static final RoutingDecision DECISION_LEGACY = new RoutingDecision(
            BackendDestinatario.MYPAY, ModalitaRouting.LEGACY, "http://localhost:8080");

    @BeforeEach
    void setUp() {
        service = new TransactionLoggingService(transactionLogRepository);
    }

    @Nested
    @DisplayName("logSuccesso")
    class LogSuccesso {

        @Test
        @DisplayName("Transazione PU con successo — registra con esito OK")
        void logSuccesso_PU_registraConEsitoOk() {
            service.logSuccesso("ENTE_TEST", "pivotSILAutorizzaImportFlussoTesoreria",
                    DECISION_PU, "/ws/pivot/Pagamenti", 200, 150L);

            verify(transactionLogRepository).insert(
                    eq("ENTE_TEST"),
                    eq("pivotSILAutorizzaImportFlussoTesoreria"),
                    eq("PIATTAFORMA_UNITARIA"),
                    eq("MYPIVOT"),
                    eq("/ws/pivot/Pagamenti"),
                    eq(200),
                    eq("OK"),
                    isNull(),
                    eq(150L)
            );
        }

        @Test
        @DisplayName("Transazione legacy con successo — registra con destinazione MYPAY")
        void logSuccesso_legacy_registraConDestinazioneMypay() {
            service.logSuccesso("ENTE_LEGACY", "operazione",
                    DECISION_LEGACY, "/ws/pa/endpoint", 200, 80L);

            verify(transactionLogRepository).insert(
                    eq("ENTE_LEGACY"),
                    eq("operazione"),
                    eq("LEGACY"),
                    eq("MYPAY"),
                    eq("/ws/pa/endpoint"),
                    eq(200),
                    eq("OK"),
                    isNull(),
                    eq(80L)
            );
        }
    }

    @Nested
    @DisplayName("logErrore")
    class LogErrore {

        @Test
        @DisplayName("Errore con decision disponibile — registra modalita e destinazione")
        void logErrore_conDecision_registraModalitaEDestinazione() {
            service.logErrore("ENTE_TEST", "operazione",
                    DECISION_PU, "/ws/pivot/Pagamenti", 500,
                    "Errore di comunicazione", 200L);

            verify(transactionLogRepository).insert(
                    eq("ENTE_TEST"),
                    eq("operazione"),
                    eq("PIATTAFORMA_UNITARIA"),
                    eq("MYPIVOT"),
                    eq("/ws/pivot/Pagamenti"),
                    eq(500),
                    eq("ERRORE"),
                    eq("Errore di comunicazione"),
                    eq(200L)
            );
        }

        @Test
        @DisplayName("Errore con decision null — registra SCONOSCIUTA")
        void logErrore_decisionNull_registraSconosciuta() {
            service.logErrore("ENTE_TEST", "operazione",
                    null, "/ws/pivot/Pagamenti", null,
                    "Ente non censito", 50L);

            verify(transactionLogRepository).insert(
                    eq("ENTE_TEST"),
                    eq("operazione"),
                    eq("SCONOSCIUTA"),
                    eq("SCONOSCIUTA"),
                    eq("/ws/pivot/Pagamenti"),
                    isNull(),
                    eq("ERRORE"),
                    eq("Ente non censito"),
                    eq(50L)
            );
        }
    }

    @Nested
    @DisplayName("logErrorePreRouting")
    class LogErrorePreRouting {

        @Test
        @DisplayName("Errore pre-routing — modalita e destinazione sono SCONOSCIUTA")
        void logErrorePreRouting_registraSconosciuta() {
            service.logErrorePreRouting("ENTE_TEST", "operazione",
                    "/ws/pivot/Pagamenti", "codIpaEnte non trovato nell'Header", 30L);

            verify(transactionLogRepository).insert(
                    eq("ENTE_TEST"),
                    eq("operazione"),
                    eq("SCONOSCIUTA"),
                    eq("SCONOSCIUTA"),
                    eq("/ws/pivot/Pagamenti"),
                    isNull(),
                    eq("ERRORE"),
                    eq("codIpaEnte non trovato nell'Header"),
                    eq(30L)
            );
        }

        @Test
        @DisplayName("Errore pre-routing con parametri null — usa N/A come fallback")
        void logErrorePreRouting_parametriNull_usaNa() {
            service.logErrorePreRouting(null, null, null,
                    "Errore generico", 10L);

            verify(transactionLogRepository).insert(
                    eq("N/A"),
                    eq("N/A"),
                    eq("SCONOSCIUTA"),
                    eq("SCONOSCIUTA"),
                    eq("N/A"),
                    isNull(),
                    eq("ERRORE"),
                    eq("Errore generico"),
                    eq(10L)
            );
        }
    }

    @Nested
    @DisplayName("Troncamento messaggi di errore")
    class TroncamentoMessaggi {

        @Test
        @DisplayName("Messaggio di errore oltre 1000 caratteri viene troncato")
        void logErrore_messaggioLungo_vieneTroncato() {
            // Crea un messaggio di 1500 caratteri
            String messaggioLungo = "E".repeat(1500);

            service.logErrore("ENTE_TEST", "operazione",
                    DECISION_PU, "/ws/pivot/Pagamenti", 500,
                    messaggioLungo, 200L);

            verify(transactionLogRepository).insert(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyInt(), anyString(),
                    argThat(msg -> msg.length() == 1000 && msg.endsWith("...")),
                    anyLong()
            );
        }

        @Test
        @DisplayName("Messaggio di errore sotto 1000 caratteri non viene troncato")
        void logErrore_messaggioCorto_nonVieneTroncato() {
            String messaggioCorto = "Errore breve";

            service.logErrore("ENTE_TEST", "operazione",
                    DECISION_PU, "/ws/pivot/Pagamenti", 500,
                    messaggioCorto, 100L);

            verify(transactionLogRepository).insert(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyInt(), anyString(),
                    eq("Errore breve"),
                    anyLong()
            );
        }

        @Test
        @DisplayName("Messaggio di errore null — resta null")
        void logErrorePreRouting_messaggioNull_restNull() {
            service.logErrorePreRouting("ENTE_TEST", "operazione",
                    "/ws/pivot/Pagamenti", null, 10L);

            verify(transactionLogRepository).insert(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), isNull(), anyString(),
                    isNull(),
                    anyLong()
            );
        }
    }

    @Nested
    @DisplayName("Resilienza — errore DB non blocca il flusso")
    class ResilienzaErroreDb {

        @Test
        @DisplayName("Errore DB durante logSuccesso — non rilancia eccezione")
        void logSuccesso_erroreDb_nonRilanciaEccezione() {
            doThrow(new RuntimeException("Connessione DB persa"))
                    .when(transactionLogRepository).insert(
                            anyString(), anyString(), anyString(), anyString(),
                            anyString(), any(), anyString(), any(), anyLong());

            // Non deve lanciare eccezione
            service.logSuccesso("ENTE_TEST", "operazione",
                    DECISION_PU, "/ws/pivot/Pagamenti", 200, 100L);

            // Verifica che il metodo insert sia stato comunque chiamato
            verify(transactionLogRepository).insert(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any(), anyLong());
        }

        @Test
        @DisplayName("Errore DB durante logErrore — non rilancia eccezione")
        void logErrore_erroreDb_nonRilanciaEccezione() {
            doThrow(new RuntimeException("Timeout DB"))
                    .when(transactionLogRepository).insert(
                            anyString(), anyString(), anyString(), anyString(),
                            anyString(), any(), anyString(), any(), anyLong());

            // Non deve lanciare eccezione
            service.logErrore("ENTE_TEST", "operazione",
                    DECISION_PU, "/ws/pivot/Pagamenti", 500,
                    "Errore originale", 200L);

            verify(transactionLogRepository).insert(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any(), anyLong());
        }
    }
}
