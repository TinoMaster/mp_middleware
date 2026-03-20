package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.EnteConfig;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

/**
 * DAO Jdbi per la tabella {@code mwpay_ente_config}.
 *
 * <p>Fornisce le operazioni di accesso ai dati per la configurazione di routing
 * degli enti. Utilizza il pattern SQL Object di Jdbi con annotazioni dichiarative.
 *
 * <p>La query principale e' {@link #findByCodIpaEnteAndTipoOperazione(String, String)}
 * utilizzata dal servizio di routing per determinare la modalita di instradamento.
 *
 * <p>Questo DAO viene registrato come bean Spring tramite
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration}.
 *
 * @see EnteConfig
 * @see EnteConfigRowMapper
 */
@RegisterRowMapper(EnteConfigRowMapper.class)
public interface EnteConfigRepository {

    /**
     * Recupera la configurazione di routing per uno specifico ente e tipo di operazione,
     * limitando ai record attivi.
     *
     * <p>Questa e' la query principale usata dal {@code RoutingDecisionService}
     * per determinare se instradare verso la Piattaforma Unitaria o il backend legacy.
     *
     * @param codIpaEnte      codice IPA dell'ente (es. {@code "R_LOMBARDIA"})
     * @param tipoOperazione  tipo di operazione SOAP (es. {@code "pivotSILAutorizzaImportFlussoTesoreria"})
     * @return configurazione dell'ente se presente e attiva, vuoto altrimenti
     */
    @SqlQuery("SELECT * FROM mwpay_ente_config "
            + "WHERE cod_ipa_ente = :codIpaEnte "
            + "AND tipo_operazione = :tipoOperazione "
            + "AND attivo = TRUE")
    Optional<EnteConfig> findByCodIpaEnteAndTipoOperazione(
            @Bind("codIpaEnte") String codIpaEnte,
            @Bind("tipoOperazione") String tipoOperazione);

    /**
     * Recupera tutte le configurazioni attive per un determinato ente.
     *
     * @param codIpaEnte codice IPA dell'ente
     * @return lista delle configurazioni attive dell'ente (vuota se non censito)
     */
    @SqlQuery("SELECT * FROM mwpay_ente_config "
            + "WHERE cod_ipa_ente = :codIpaEnte "
            + "AND attivo = TRUE "
            + "ORDER BY tipo_operazione")
    List<EnteConfig> findAllByCodIpaEnte(@Bind("codIpaEnte") String codIpaEnte);

    /**
     * Recupera tutte le configurazioni attive presenti nel sistema.
     * Utilizzata per la cache in-memory e per diagnostica.
     *
     * @return lista di tutte le configurazioni attive
     */
    @SqlQuery("SELECT * FROM mwpay_ente_config "
            + "WHERE attivo = TRUE "
            + "ORDER BY cod_ipa_ente, tipo_operazione")
    List<EnteConfig> findAllAttive();

    /**
     * Conta il numero di configurazioni attive nel sistema.
     * Utilizzata dall'health check per verificare che esistano regole di routing.
     *
     * @return numero di record attivi
     */
    @SqlQuery("SELECT COUNT(*) FROM mwpay_ente_config WHERE attivo = TRUE")
    long countAttive();

    /**
     * Inserisce una nuova configurazione di routing per un ente.
     *
     * @param codIpaEnte       codice IPA dell'ente
     * @param tipoOperazione   tipo di operazione SOAP
     * @param modalitaRouting  modalita di instradamento ({@code PIATTAFORMA_UNITARIA} o {@code LEGACY})
     * @param note             note libere (opzionale)
     */
    @SqlUpdate("INSERT INTO mwpay_ente_config (cod_ipa_ente, tipo_operazione, modalita_routing, attivo, note) "
            + "VALUES (:codIpaEnte, :tipoOperazione, :modalitaRouting, TRUE, :note)")
    void insert(@Bind("codIpaEnte") String codIpaEnte,
                @Bind("tipoOperazione") String tipoOperazione,
                @Bind("modalitaRouting") String modalitaRouting,
                @Bind("note") String note);

    /**
     * Aggiorna la modalita di routing per una configurazione esistente.
     *
     * @param codIpaEnte       codice IPA dell'ente
     * @param tipoOperazione   tipo di operazione SOAP
     * @param modalitaRouting  nuova modalita di instradamento
     */
    @SqlUpdate("UPDATE mwpay_ente_config "
            + "SET modalita_routing = :modalitaRouting, data_aggiornamento = NOW() "
            + "WHERE cod_ipa_ente = :codIpaEnte "
            + "AND tipo_operazione = :tipoOperazione")
    void updateModalitaRouting(@Bind("codIpaEnte") String codIpaEnte,
                                @Bind("tipoOperazione") String tipoOperazione,
                                @Bind("modalitaRouting") String modalitaRouting);
}
