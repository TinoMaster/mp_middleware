package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.TransactionLog;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * DAO Jdbi per la tabella {@code mwpay_transaction_log}.
 *
 * <p>Fornisce le operazioni di scrittura per il log transazionale del middleware.
 * Ogni richiesta SOAP processata genera un record in questa tabella per garantire
 * tracciabilita completa.
 *
 * <p>Le operazioni di lettura (reporting, diagnostica) potranno essere aggiunte
 * in futuro se necessarie. Attualmente il focus e' sull'inserimento sincrono
 * post-request.
 *
 * <p>Questo DAO viene registrato come bean Spring tramite
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration}.
 *
 * @see TransactionLog
 * @see TransactionLogRowMapper
 */
@RegisterRowMapper(TransactionLogRowMapper.class)
public interface TransactionLogRepository {

    /**
     * Inserisce un nuovo record di log transazionale.
     *
     * <p>Chiamato dopo ogni richiesta SOAP processata dal middleware, sia in caso
     * di successo che di errore. Se l'inserimento fallisce, il chiamante deve
     * gestire l'errore senza bloccare la risposta al SIL.
     *
     * @param codIpaEnte         codice IPA dell'ente
     * @param tipoOperazione     tipo di operazione SOAP (local part)
     * @param modalitaRouting    modalita di instradamento utilizzata
     * @param destinazione       backend di destinazione ({@code "MYPAY"} o {@code "MYPIVOT"})
     * @param pathRichiesta      path HTTP della richiesta SOAP
     * @param httpStatusRisposta codice HTTP della risposta dal backend (null se non disponibile)
     * @param esito              esito della transazione ({@code "OK"} o {@code "ERRORE"})
     * @param messaggioErrore    messaggio di errore (null se esito = OK)
     * @param durataMs           durata della transazione in millisecondi
     */
    @SqlUpdate("INSERT INTO mwpay_transaction_log "
            + "(cod_ipa_ente, tipo_operazione, modalita_routing, destinazione, "
            + "path_richiesta, http_status_risposta, esito, messaggio_errore, durata_ms) "
            + "VALUES (:codIpaEnte, :tipoOperazione, :modalitaRouting, :destinazione, "
            + ":pathRichiesta, :httpStatusRisposta, :esito, :messaggioErrore, :durataMs)")
    void insert(@Bind("codIpaEnte") String codIpaEnte,
                @Bind("tipoOperazione") String tipoOperazione,
                @Bind("modalitaRouting") String modalitaRouting,
                @Bind("destinazione") String destinazione,
                @Bind("pathRichiesta") String pathRichiesta,
                @Bind("httpStatusRisposta") Integer httpStatusRisposta,
                @Bind("esito") String esito,
                @Bind("messaggioErrore") String messaggioErrore,
                @Bind("durataMs") Long durataMs);
}
