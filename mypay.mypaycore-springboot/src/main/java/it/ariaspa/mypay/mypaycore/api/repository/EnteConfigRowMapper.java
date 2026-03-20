package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.EnteConfig;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Mapper Jdbi che converte le righe della tabella {@code mwpay_ente_config}
 * in istanze di {@link EnteConfig}.
 *
 * <p>Registrato automaticamente come bean Spring e rilevato da
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration} che lo installa
 * sull'istanza Jdbi al bootstrap.
 *
 * <p>La conversione della colonna {@code modalita_routing} avviene tramite
 * {@link ModalitaRouting#valueOf(String)}: il valore in DB deve corrispondere
 * esattamente a uno dei valori dell'enum ({@code PIATTAFORMA_UNITARIA} o {@code LEGACY}).
 */
@Component
public class EnteConfigRowMapper implements RowMapper<EnteConfig> {

    /**
     * Converte una singola riga del {@link ResultSet} in un oggetto {@link EnteConfig}.
     *
     * @param rs  result set posizionato sulla riga corrente
     * @param ctx contesto della query Jdbi
     * @return istanza di {@link EnteConfig} con tutti i campi popolati
     * @throws SQLException se si verifica un errore di accesso al result set
     */
    @Override
    public EnteConfig map(ResultSet rs, StatementContext ctx) throws SQLException {
        EnteConfig config = new EnteConfig();
        config.setId(rs.getLong("id"));
        config.setCodIpaEnte(rs.getString("cod_ipa_ente"));
        config.setTipoOperazione(rs.getString("tipo_operazione"));
        config.setModalitaRouting(ModalitaRouting.valueOf(rs.getString("modalita_routing")));
        config.setAttivo(rs.getBoolean("attivo"));
        config.setNote(rs.getString("note"));

        Timestamp dataCreazione = rs.getTimestamp("data_creazione");
        if (dataCreazione != null) {
            config.setDataCreazione(dataCreazione.toLocalDateTime());
        }

        Timestamp dataAggiornamento = rs.getTimestamp("data_aggiornamento");
        if (dataAggiornamento != null) {
            config.setDataAggiornamento(dataAggiornamento.toLocalDateTime());
        }

        return config;
    }
}
