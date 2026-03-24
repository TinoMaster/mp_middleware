package it.ariaspa.mypay.mypaycore.api.routing;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import lombok.Getter;

/**
 * Risultato della decisione di routing per una richiesta SOAP.
 *
 * <p>Prodotto dal {@link RoutingDecisionService}, contiene tutte le informazioni
 * necessarie all'endpoint SOAP per instradare la richiesta:
 * <ul>
 *   <li>{@link #destinazione} — il backend di destinazione (MYPAY o MYPIVOT),
 *       determinato dal path della richiesta tramite {@code PathRegistryConfig}</li>
 *   <li>{@link #modalita} — la modalita di instradamento (PU con OAuth2 o legacy diretto),
 *       determinata dalla configurazione dell'ente nel database</li>
 *   <li>{@link #urlBackend} — l'URL base del backend di destinazione,
 *       determinato da {@code BackendRoutingConfig}</li>
 * </ul>
 *
 * <p>Questa classe e' immutabile: una volta creata, non puo' essere modificata.
 *
 * @see RoutingDecisionService
 */
@Getter
public class RoutingDecision {

    /** Backend di destinazione (MYPAY o MYPIVOT).
     * -- GETTER --
     *  Restituisce il backend di destinazione.
     *
     * @return il backend (MYPAY o MYPIVOT)
     */
    private final BackendDestinatario destinazione;

    /** Modalita di instradamento (PIATTAFORMA_UNITARIA o LEGACY).
     * -- GETTER --
     *  Restituisce la modalita di instradamento.
     *
     * @return la modalita (PIATTAFORMA_UNITARIA o LEGACY)
     */
    private final ModalitaRouting modalita;

    /** URL base del backend di destinazione.
     * -- GETTER --
     *  Restituisce l'URL base del backend di destinazione.
     *
     * @return l'URL base del backend
     */
    private final String urlBackend;

    /**
     * Crea una nuova decisione di routing.
     *
     * @param destinazione il backend di destinazione
     * @param modalita     la modalita di instradamento
     * @param urlBackend   l'URL base del backend
     */
    public RoutingDecision(BackendDestinatario destinazione, ModalitaRouting modalita, String urlBackend) {
        this.destinazione = destinazione;
        this.modalita = modalita;
        this.urlBackend = urlBackend;
    }

    /**
     * Verifica se la richiesta deve essere instradata verso la Piattaforma Unitaria.
     *
     * @return {@code true} se la modalita e' PIATTAFORMA_UNITARIA
     */
    public boolean isPiattaformaUnitaria() {
        return modalita == ModalitaRouting.PIATTAFORMA_UNITARIA;
    }

    /**
     * Verifica se la richiesta deve essere instradata verso il backend legacy.
     *
     * @return {@code true} se la modalita e' LEGACY
     */
    public boolean isLegacy() {
        return modalita == ModalitaRouting.LEGACY;
    }

    @Override
    public String toString() {
        return "RoutingDecision{" +
                "destinazione=" + destinazione +
                ", modalita=" + modalita +
                ", urlBackend='" + urlBackend + '\'' +
                '}';
    }
}
