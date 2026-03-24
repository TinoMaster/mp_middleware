package it.ariaspa.mypay.mypaycore.api.routing;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.EnteCompleto;
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
 *   <li>{@link #modalita} — la modalita' di instradamento (PU con OAuth2 o legacy diretto),
 *       derivata dalla presenza di una configurazione PU attiva per l'ente</li>
 *   <li>{@link #urlBackend} — l'URL base del backend di destinazione,
 *       determinato da {@code BackendRoutingConfig}</li>
 *   <li>{@link #ente} — i dati completi dell'ente (anagrafica + configurazione PU),
 *       necessari per recuperare le credenziali OAuth2 per-ente</li>
 * </ul>
 *
 * <p>Questa classe e' immutabile: una volta creata, non puo' essere modificata.
 *
 * @see RoutingDecisionService
 */
@Getter
public class RoutingDecision {

    /** Backend di destinazione (MYPAY o MYPIVOT). */
    private final BackendDestinatario destinazione;

    /** Modalita' di instradamento (PIATTAFORMA_UNITARIA o LEGACY). */
    private final ModalitaRouting modalita;

    /** URL base del backend di destinazione. */
    private final String urlBackend;

    /**
     * Dati completi dell'ente, inclusa la configurazione PU opzionale.
     * Contiene le credenziali OAuth2 ({@code client_id}, {@code client_secret}) se
     * il routing e' verso la Piattaforma Unitaria.
     */
    private final EnteCompleto ente;

    /**
     * Crea una nuova decisione di routing.
     *
     * @param destinazione il backend di destinazione
     * @param modalita     la modalita' di instradamento
     * @param urlBackend   l'URL base del backend
     * @param ente         i dati completi dell'ente (anagrafica + configurazione PU)
     */
    public RoutingDecision(BackendDestinatario destinazione, ModalitaRouting modalita,
                           String urlBackend, EnteCompleto ente) {
        this.destinazione = destinazione;
        this.modalita = modalita;
        this.urlBackend = urlBackend;
        this.ente = ente;
    }

    /**
     * Verifica se la richiesta deve essere instradata verso la Piattaforma Unitaria.
     *
     * @return {@code true} se la modalita' e' PIATTAFORMA_UNITARIA
     */
    public boolean isPiattaformaUnitaria() {
        return modalita == ModalitaRouting.PIATTAFORMA_UNITARIA;
    }

    /**
     * Verifica se la richiesta deve essere instradata verso il backend legacy.
     *
     * @return {@code true} se la modalita' e' LEGACY
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
                ", ente=" + ente +
                '}';
    }
}
