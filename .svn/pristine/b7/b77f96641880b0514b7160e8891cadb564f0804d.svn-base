package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.EnteConfigPu;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;
import java.util.Optional;

/**
 * DAO Jdbi per la tabella {@code mygov_ente_config_pu}.
 *
 * <p>Fornisce accesso alle configurazioni OAuth2 degli enti verso la
 * Piattaforma Unitaria. Un ente che ha un record attivo in questa tabella
 * viene instradato tramite flusso OAuth2; altrimenti viene usato il flusso legacy.
 *
 * <p>Questo DAO viene registrato come bean Spring tramite
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration}.
 *
 * @see EnteConfigPu
 */
@RegisterRowMapper(EnteConfigPuRowMapper.class)
public interface EnteConfigPuRepository {

    /**
     * Recupera la configurazione PU per un ente specifico.
     *
     * <p>Restituisce la configurazione indipendentemente dal flag {@code attivo}:
     * la logica di attivazione e' gestita in {@link it.ariaspa.mypay.mypaycore.api.domain.EnteCompleto}.
     *
     * @param codiceIpaEnte codice IPA dell'ente
     * @return configurazione PU se presente, {@link Optional#empty()} altrimenti
     */
    @SqlQuery("SELECT id, codice_ipa_ente, client_id, client_secret, attivo, "
            + "dt_creazione, dt_ultima_modifica "
            + "FROM mygov_ente_config_pu "
            + "WHERE codice_ipa_ente = :codiceIpaEnte")
    Optional<EnteConfigPu> findByCodiceIpaEnte(@Bind("codiceIpaEnte") String codiceIpaEnte);

    /**
     * Recupera tutte le configurazioni PU presenti nel sistema.
     * Usata per la cache e per diagnostica.
     *
     * @return lista di tutte le configurazioni PU
     */
    @SqlQuery("SELECT id, codice_ipa_ente, client_id, client_secret, attivo, "
            + "dt_creazione, dt_ultima_modifica "
            + "FROM mygov_ente_config_pu "
            + "ORDER BY codice_ipa_ente")
    List<EnteConfigPu> findAll();

    /**
     * Conta il numero di configurazioni PU attive.
     * Utilizzata dall'health check.
     *
     * @return numero di record con {@code attivo = TRUE}
     */
    @SqlQuery("SELECT COUNT(*) FROM mygov_ente_config_pu WHERE attivo = TRUE")
    long countAttive();
}
