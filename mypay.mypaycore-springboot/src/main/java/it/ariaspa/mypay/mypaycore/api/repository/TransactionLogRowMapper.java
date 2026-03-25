package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.domain.TransactionLog;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Mapper Jdbi che converte le righe della tabella {@code mygov_mw_transaction_log}
 * in istanze di {@link TransactionLog}.
 *
 * <p>Registrato automaticamente come bean Spring e rilevato da
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration} che lo installa
 * sull'istanza Jdbi al bootstrap.
 */
@Component
public class TransactionLogRowMapper implements RowMapper<TransactionLog> {

    /**
     * Converte una singola riga del {@link ResultSet} in un oggetto {@link TransactionLog}.
     *
     * @param rs  result set posizionato sulla riga corrente
     * @param ctx contesto della query Jdbi
     * @return istanza di {@link TransactionLog} con tutti i campi popolati
     * @throws SQLException se si verifica un errore di accesso al result set
     */
    @Override
    public TransactionLog map(ResultSet rs, StatementContext ctx) throws SQLException {
        TransactionLog log = new TransactionLog();
        log.setId(rs.getLong("id"));
        log.setCodIpaEnte(rs.getString("cod_ipa_ente"));
        log.setTipoOperazione(rs.getString("tipo_operazione"));
        log.setModalitaRouting(ModalitaRouting.valueOf(rs.getString("modalita_routing")));
        log.setDestinazione(rs.getString("destinazione"));
        log.setPathRichiesta(rs.getString("path_richiesta"));

        int httpStatus = rs.getInt("http_status_risposta");
        if (!rs.wasNull()) {
            log.setHttpStatusRisposta(httpStatus);
        }

        log.setEsito(rs.getString("esito"));
        log.setMessaggioErrore(rs.getString("messaggio_errore"));

        long durataMs = rs.getLong("durata_ms");
        if (!rs.wasNull()) {
            log.setDurataMs(durataMs);
        }

        Timestamp timestampRichiesta = rs.getTimestamp("timestamp_richiesta");
        if (timestampRichiesta != null) {
            log.setTimestampRichiesta(timestampRichiesta.toLocalDateTime());
        }

        return log;
    }
}
