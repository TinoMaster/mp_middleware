package it.ariaspa.mypay.mypaycore.api.logging;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.repository.TransactionLogRepository;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servizio per il logging transazionale delle richieste SOAP processate dal middleware.
 *
 * <p>Ogni richiesta SOAP ricevuta dal middleware genera un record nella tabella
 * {@code mwpay_transaction_log} tramite questo servizio. Il logging e' sincrono
 * (post-request) ma <strong>non bloccante</strong>: se l'inserimento in DB fallisce,
 * viene registrato un warning nel log applicativo senza interrompere la risposta al SIL.
 *
 * <p>Il servizio offre due metodi principali:
 * <ul>
 *   <li>{@link #logSuccesso} — registra una transazione completata con successo</li>
 *   <li>{@link #logErrore} — registra una transazione fallita con messaggio di errore</li>
 * </ul>
 *
 * <p>Informazioni registrate per ogni transazione:
 * <ul>
 *   <li>Codice IPA dell'ente ({@code codIpaEnte})</li>
 *   <li>Tipo di operazione SOAP ({@code tipoOperazione})</li>
 *   <li>Modalita di instradamento (PU o legacy)</li>
 *   <li>Backend di destinazione (MYPAY o MYPIVOT)</li>
 *   <li>Path HTTP della richiesta</li>
 *   <li>Codice HTTP della risposta dal backend</li>
 *   <li>Esito (OK o ERRORE)</li>
 *   <li>Messaggio di errore (solo in caso di errore, senza dati sensibili)</li>
 *   <li>Durata in millisecondi</li>
 * </ul>
 *
 * @see TransactionLogRepository
 * @see it.ariaspa.mypay.mypaycore.api.domain.TransactionLog
 */
@Service
public class TransactionLoggingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionLoggingService.class);

    /** Esito per transazioni completate con successo. */
    static final String ESITO_OK = "OK";

    /** Esito per transazioni fallite. */
    static final String ESITO_ERRORE = "ERRORE";

    /** Lunghezza massima del messaggio di errore salvato nel DB (evita overflow). */
    private static final int MAX_MESSAGGIO_ERRORE_LENGTH = 1000;

    private final TransactionLogRepository transactionLogRepository;

    /**
     * Crea il servizio di logging con il repository iniettato.
     *
     * @param transactionLogRepository repository Jdbi per l'inserimento dei log
     */
    public TransactionLoggingService(TransactionLogRepository transactionLogRepository) {
        this.transactionLogRepository = transactionLogRepository;
    }

    /**
     * Registra una transazione completata con successo.
     *
     * <p>Se l'inserimento nel DB fallisce, il metodo non rilancia l'eccezione:
     * registra un warning nel log applicativo e prosegue. La risposta al SIL
     * non deve mai essere bloccata da un errore di logging.
     *
     * @param codIpaEnte     codice IPA dell'ente
     * @param tipoOperazione tipo di operazione SOAP
     * @param decision       decisione di routing (contiene destinazione, modalita, URL)
     * @param pathRichiesta  path HTTP della richiesta SOAP
     * @param httpStatus     codice HTTP della risposta dal backend (null se non disponibile)
     * @param durataMs       durata della transazione in millisecondi
     */
    public void logSuccesso(String codIpaEnte, String tipoOperazione,
                            RoutingDecision decision, String pathRichiesta,
                            Integer httpStatus, long durataMs) {
        inserisciLog(codIpaEnte, tipoOperazione, decision.getModalita(),
                decision.getDestinazione(), pathRichiesta, httpStatus,
                ESITO_OK, null, durataMs);
    }

    /**
     * Registra una transazione fallita con messaggio di errore.
     *
     * <p>Il messaggio di errore viene troncato a {@value #MAX_MESSAGGIO_ERRORE_LENGTH}
     * caratteri per evitare overflow nella colonna del DB. I dati sensibili non devono
     * essere inclusi nel messaggio.
     *
     * <p>Se l'inserimento nel DB fallisce, il metodo non rilancia l'eccezione.
     *
     * @param codIpaEnte       codice IPA dell'ente
     * @param tipoOperazione   tipo di operazione SOAP
     * @param decision         decisione di routing (puo' essere null se l'errore avviene
     *                         prima della decisione di routing)
     * @param pathRichiesta    path HTTP della richiesta SOAP
     * @param httpStatus       codice HTTP della risposta dal backend (null se non disponibile)
     * @param messaggioErrore  messaggio di errore (senza dati sensibili)
     * @param durataMs         durata della transazione in millisecondi
     */
    public void logErrore(String codIpaEnte, String tipoOperazione,
                          RoutingDecision decision, String pathRichiesta,
                          Integer httpStatus, String messaggioErrore, long durataMs) {
        ModalitaRouting modalita = decision != null ? decision.getModalita() : null;
        BackendDestinatario destinazione = decision != null ? decision.getDestinazione() : null;

        inserisciLog(codIpaEnte, tipoOperazione, modalita, destinazione,
                pathRichiesta, httpStatus, ESITO_ERRORE,
                truncate(messaggioErrore), durataMs);
    }

    /**
     * Registra un errore avvenuto prima che la decisione di routing fosse possibile.
     *
     * <p>Utilizzato quando l'errore si verifica durante l'estrazione dei parametri
     * dalla richiesta SOAP (es. codIpaEnte non trovato nell'Header, path non riconosciuto)
     * e quindi non c'e' una RoutingDecision disponibile.
     *
     * @param codIpaEnte      codice IPA dell'ente (puo' essere null se non estratto)
     * @param tipoOperazione  tipo di operazione SOAP (puo' essere null)
     * @param pathRichiesta   path HTTP della richiesta SOAP
     * @param messaggioErrore messaggio di errore (senza dati sensibili)
     * @param durataMs        durata della transazione in millisecondi
     */
    public void logErrorePreRouting(String codIpaEnte, String tipoOperazione,
                                     String pathRichiesta, String messaggioErrore,
                                     long durataMs) {
        inserisciLog(codIpaEnte, tipoOperazione, null, null,
                pathRichiesta, null, ESITO_ERRORE,
                truncate(messaggioErrore), durataMs);
    }

    /**
     * Inserisce un record di log nella tabella {@code mwpay_transaction_log}.
     *
     * <p>Protetto da try-catch: qualsiasi errore di DB viene catturato e loggato
     * come warning senza propagazione. Questo garantisce che il logging non
     * interferisca mai con la risposta al SIL.
     */
    private void inserisciLog(String codIpaEnte, String tipoOperazione,
                              ModalitaRouting modalita, BackendDestinatario destinazione,
                              String pathRichiesta, Integer httpStatus,
                              String esito, String messaggioErrore, long durataMs) {
        try {
            String modalitaStr = modalita != null ? modalita.name() : "SCONOSCIUTA";
            String destinazioneStr = destinazione != null ? destinazione.name() : "SCONOSCIUTA";

            transactionLogRepository.insert(
                    codIpaEnte != null ? codIpaEnte : "N/A",
                    tipoOperazione != null ? tipoOperazione : "N/A",
                    modalitaStr,
                    destinazioneStr,
                    pathRichiesta != null ? pathRichiesta : "N/A",
                    httpStatus,
                    esito,
                    messaggioErrore,
                    durataMs
            );

            log.debug("Log transazionale registrato: ente='{}', operazione='{}', "
                            + "modalita={}, destinazione={}, esito={}, durata={}ms",
                    codIpaEnte, tipoOperazione, modalitaStr, destinazioneStr, esito, durataMs);

        } catch (Exception e) {
            // Non bloccare MAI la risposta al SIL per un errore di logging
            log.warn("Errore durante l'inserimento del log transazionale — la risposta "
                            + "al SIL non viene bloccata. Dettagli: ente='{}', operazione='{}', "
                            + "esito={}, errore='{}'",
                    codIpaEnte, tipoOperazione, esito, e.getMessage());
        }
    }

    /**
     * Tronca il messaggio di errore alla lunghezza massima consentita.
     *
     * @param messaggio il messaggio da troncare (puo' essere null)
     * @return il messaggio troncato con "..." se necessario, null se il messaggio era null
     */
    private String truncate(String messaggio) {
        if (messaggio == null) {
            return null;
        }
        if (messaggio.length() <= MAX_MESSAGGIO_ERRORE_LENGTH) {
            return messaggio;
        }
        return messaggio.substring(0, MAX_MESSAGGIO_ERRORE_LENGTH - 3) + "...";
    }
}
