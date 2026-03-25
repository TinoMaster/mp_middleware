package it.ariaspa.mypay.mypaycore.api.repository;

import it.ariaspa.mypay.mypaycore.api.domain.Ente;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;
import java.util.Optional;

/**
 * DAO Jdbi per la tabella condivisa {@code mygov_ente}.
 *
 * <p>Fornisce accesso in sola lettura ai dati anagrafici degli enti pubblici.
 * La tabella e' condivisa con le applicazioni mypay e mypivot e non deve
 * essere modificata dal middleware.
 *
 * <p>Questo DAO viene registrato come bean Spring tramite
 * {@link it.ariaspa.mypay.mypaycore.api.config.JdbiConfiguration}.
 *
 * @see Ente
 */
@RegisterRowMapper(EnteRowMapper.class)
public interface EnteRepository {

    /**
     * Verifica se un ente e' censito nel sistema cercandolo per codice IPA.
     *
     * <p>Questa e' la query principale usata per la validazione: se il codice IPA
     * non e' presente in {@code mygov_ente}, la richiesta viene rifiutata
     * con SOAP Fault {@code ENTE_NON_AUTORIZZATO}.
     *
     * @param codIpaEnte codice IPA dell'ente (es. {@code "R_LOMBARDIA"})
     * @return dati dell'ente se censito, {@link Optional#empty()} altrimenti
     */
    @SqlQuery("SELECT mygov_ente_id, cod_ipa_ente, codice_fiscale_ente, de_nome_ente, cd_stato_ente "
            + "FROM mygov_ente "
            + "WHERE cod_ipa_ente = :codIpaEnte")
    Optional<Ente> findByCodIpaEnte(@Bind("codIpaEnte") String codIpaEnte);

    /**
     * Cerca un ente per codice fiscale.
     *
     * <p>Usato per risolvere l'{@code identificativoDominio} presente negli header SOAP
     * dei servizi MyPay (CCPPa, Esito, CCP, CCP25, RT, RP, AvvisiDigitali)
     * nel corrispondente {@code codIpaEnte} per il routing.
     *
     * @param codiceFiscaleEnte codice fiscale dell'ente (es. {@code "80007580279"})
     * @return dati dell'ente se trovato, {@link Optional#empty()} altrimenti
     */
    @SqlQuery("SELECT mygov_ente_id, cod_ipa_ente, codice_fiscale_ente, de_nome_ente, cd_stato_ente "
            + "FROM mygov_ente "
            + "WHERE codice_fiscale_ente = :codiceFiscaleEnte")
    Optional<Ente> findByCodiceFiscale(@Bind("codiceFiscaleEnte") String codiceFiscaleEnte);

    /**
     * Recupera tutti gli enti presenti nel sistema.
     * Usata per la cache completa e per diagnostica.
     *
     * @return lista di tutti gli enti
     */
    @SqlQuery("SELECT mygov_ente_id, cod_ipa_ente, codice_fiscale_ente, de_nome_ente, cd_stato_ente "
            + "FROM mygov_ente "
            + "ORDER BY cod_ipa_ente")
    List<Ente> findAll();

    /**
     * Conta il numero totale di enti registrati nel sistema.
     *
     * @return numero di enti
     */
    @SqlQuery("SELECT COUNT(*) FROM mygov_ente")
    long count();
}
