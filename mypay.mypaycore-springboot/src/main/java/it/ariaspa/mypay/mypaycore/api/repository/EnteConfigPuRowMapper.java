package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.EnteConfigPu;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Mapper Jdbi che converte le righe della tabella {@code mygov_ente_config_pu}
 * in istanze di {@link EnteConfigPu}.
 *
 * <p>Registrato automaticamente come bean Spring e rilevato da
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration} che lo installa
 * sull'istanza Jdbi al bootstrap.
 */
@Component
public class EnteConfigPuRowMapper implements RowMapper<EnteConfigPu> {

    /**
     * Converte una singola riga del {@link ResultSet} in un oggetto {@link EnteConfigPu}.
     *
     * @param rs  result set posizionato sulla riga corrente
     * @param ctx contesto della query Jdbi
     * @return istanza di {@link EnteConfigPu} con tutti i campi popolati
     * @throws SQLException se si verifica un errore di accesso al result set
     */
    @Override
    public EnteConfigPu map(ResultSet rs, StatementContext ctx) throws SQLException {
        EnteConfigPu config = new EnteConfigPu();
        config.setId(rs.getLong("id"));
        config.setCodiceIpaEnte(rs.getString("codice_ipa_ente"));
        config.setClientId(rs.getString("client_id"));
        config.setClientSecret(rs.getString("client_secret"));
        config.setAttivo(rs.getBoolean("attivo"));

        Timestamp dtCreazione = rs.getTimestamp("dt_creazione");
        if (dtCreazione != null) {
            config.setDtCreazione(dtCreazione.toInstant()
                    .atOffset(java.time.ZoneOffset.UTC));
        }

        Timestamp dtUltimaModifica = rs.getTimestamp("dt_ultima_modifica");
        if (dtUltimaModifica != null) {
            config.setDtUltimaModifica(dtUltimaModifica.toInstant()
                    .atOffset(java.time.ZoneOffset.UTC));
        }

        return config;
    }
}
