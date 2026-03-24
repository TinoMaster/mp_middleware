package it.ariaspa.mypay.mypaycore.api.domain;

import lombok.Getter;

import java.util.Optional;

/**
 * DTO composito che aggrega le informazioni di un ente con la sua configurazione
 * opzionale verso la Piattaforma Unitaria.
 *
 * <p>Prodotto dalla query JOIN tra {@code mygov_ente} e {@code mygov_ente_config_pu},
 * questo oggetto e' il risultato principale restituito dall'{@code EnteCacheService}
 * per ogni richiesta di lookup per {@code codIpaEnte}.
 *
 * <p>La logica di routing e' incapsulata nel metodo {@link #isPiattaformaUnitaria()}:
 * <ul>
 *   <li>{@code true} se l'ente ha una configurazione PU attiva
 *       → inoltro con OAuth2 alla Piattaforma Unitaria</li>
 *   <li>{@code false} se la configurazione PU e' assente o disattivata
 *       → forward diretto al backend legacy</li>
 * </ul>
 *
 * <p>Questa classe e' immutabile una volta creata.
 *
 * @see Ente
 * @see EnteConfigPu
 * @see it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService
 */
@Getter
public class EnteCompleto {

    /**
     * Dati anagrafici dell'ente (da {@code mygov_ente}).
     * Non puo' essere null: un {@code EnteCompleto} esiste solo se l'ente e' censito.
     */
    private final Ente ente;

    /**
     * Configurazione PU dell'ente (da {@code mygov_ente_config_pu}).
     * Puo' essere {@link Optional#empty()} se l'ente non ha configurazione PU
     * oppure se la configurazione esiste ma e' disattivata ({@code attivo = FALSE}).
     */
    private final Optional<EnteConfigPu> configPu;

    /**
     * Crea un {@code EnteCompleto} con ente e configurazione PU opzionale.
     *
     * @param ente     dati anagrafici dell'ente (non null)
     * @param configPu configurazione PU (null se assente o disattivata)
     */
    public EnteCompleto(Ente ente, EnteConfigPu configPu) {
        this.ente = ente;
        // Mantiene solo configurazioni attive
        this.configPu = (configPu != null && configPu.isAttivo())
                ? Optional.of(configPu)
                : Optional.empty();
    }

    /**
     * Restituisce il codice IPA dell'ente — shortcut per {@code getEnte().getCodIpaEnte()}.
     *
     * @return codice IPA dell'ente
     */
    public String getCodIpaEnte() {
        return ente.getCodIpaEnte();
    }

    /**
     * Indica se le richieste di questo ente devono essere inoltrate alla
     * Piattaforma Unitaria con autenticazione OAuth2.
     *
     * @return {@code true} se esiste una configurazione PU attiva per l'ente
     */
    public boolean isPiattaformaUnitaria() {
        return configPu.isPresent();
    }

    /**
     * Restituisce il {@code client_id} OAuth2 per questo ente.
     * Chiamare solo dopo aver verificato {@link #isPiattaformaUnitaria()}.
     *
     * @return client ID OAuth2
     * @throws java.util.NoSuchElementException se non esiste configurazione PU attiva
     */
    public String getClientId() {
        return configPu.get().getClientId();
    }

    /**
     * Restituisce il {@code client_secret} OAuth2 per questo ente.
     * Chiamare solo dopo aver verificato {@link #isPiattaformaUnitaria()}.
     *
     * @return client secret OAuth2
     * @throws java.util.NoSuchElementException se non esiste configurazione PU attiva
     */
    public String getClientSecret() {
        return configPu.get().getClientSecret();
    }

    @Override
    public String toString() {
        return "EnteCompleto{" +
                "codIpaEnte='" + getCodIpaEnte() + '\'' +
                ", piattaformaUnitaria=" + isPiattaformaUnitaria() +
                '}';
    }
}
