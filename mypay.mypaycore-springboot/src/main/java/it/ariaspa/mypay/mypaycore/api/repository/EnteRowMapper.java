package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.Ente;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mapper Jdbi che converte le righe della tabella {@code mygov_ente}
 * in istanze di {@link Ente}.
 *
 * <p>Registrato automaticamente come bean Spring e rilevato da
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration} che lo installa
 * sull'istanza Jdbi al bootstrap.
 */
@Component
public class EnteRowMapper implements RowMapper<Ente> {

    /**
     * Converte una singola riga del {@link ResultSet} in un oggetto {@link Ente}.
     *
     * @param rs  result set posizionato sulla riga corrente
     * @param ctx contesto della query Jdbi
     * @return istanza di {@link Ente} con i campi principali popolati
     * @throws SQLException se si verifica un errore di accesso al result set
     */
    @Override
    public Ente map(ResultSet rs, StatementContext ctx) throws SQLException {
        Ente ente = new Ente();
        ente.setMygovEnteId(rs.getLong("mygov_ente_id"));
        ente.setCodIpaEnte(rs.getString("cod_ipa_ente"));
        ente.setDeNomeEnte(rs.getString("de_nome_ente"));
        ente.setCdStatoEnte(rs.getString("cd_stato_ente"));
        return ente;
    }
}
