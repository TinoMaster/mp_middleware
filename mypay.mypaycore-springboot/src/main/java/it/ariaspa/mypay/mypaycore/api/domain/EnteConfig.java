package it.ariaspa.mypay.mypaycore.api.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Modello di dominio che rappresenta la configurazione di routing per un ente
 * e un tipo di operazione specifico.
 *
 * <p>Ogni record della tabella {@code mwpay_ente_config} indica se, per un dato
 * {@code codIpaEnte} e {@code tipoOperazione}, la richiesta SOAP deve essere
 * instradata verso la Piattaforma Unitaria (con OAuth2) o verso il backend
 * legacy (forward diretto).
 *
 * <p>La chiave logica e' la coppia {@code (codIpaEnte, tipoOperazione)}, vincolata
 * dal constraint {@code UNIQUE} a livello di database.
 *
 * <p>Il campo {@code attivo} consente di disabilitare temporaneamente una regola
 * di routing senza eliminarla dal database.
 *
 * @see ModalitaRouting
 */
@Setter
@Getter
public class EnteConfig {

    /** Identificativo univoco del record (chiave surrogata auto-generata). */
    private Long id;

    /** Codice IPA dell'ente pubblico (es. {@code "R_LOMBARDIA"}). */
    private String codIpaEnte;

    /**
     * Tipo di operazione SOAP (local part del messaggio).
     * Esempio: {@code "pivotSILAutorizzaImportFlussoTesoreria"}.
     */
    private String tipoOperazione;

    /**
     * Modalita di instradamento: {@link ModalitaRouting#PIATTAFORMA_UNITARIA}
     * o {@link ModalitaRouting#LEGACY}.
     */
    private ModalitaRouting modalitaRouting;

    /** Indica se la regola di routing e' attiva. */
    private boolean attivo;

    /** Note libere (es. motivo della configurazione o ticket di riferimento). */
    private String note;

    /** Data e ora di creazione del record. */
    private LocalDateTime dataCreazione;

    /** Data e ora dell'ultimo aggiornamento del record. */
    private LocalDateTime dataAggiornamento;

    /**
     * Costruttore vuoto per compatibilita con i framework di mapping (Jdbi, Jackson).
     */
    public EnteConfig() {
    }

    /**
     * Costruttore completo per la creazione di un'istanza con tutti i campi.
     *
     * @param id                 identificativo univoco
     * @param codIpaEnte         codice IPA dell'ente
     * @param tipoOperazione     tipo di operazione SOAP
     * @param modalitaRouting    modalita di instradamento
     * @param attivo             flag di attivazione
     * @param note               note libere
     * @param dataCreazione      data di creazione
     * @param dataAggiornamento  data dell'ultimo aggiornamento
     */
    public EnteConfig(Long id, String codIpaEnte, String tipoOperazione,
                      ModalitaRouting modalitaRouting, boolean attivo, String note,
                      LocalDateTime dataCreazione, LocalDateTime dataAggiornamento) {
        this.id = id;
        this.codIpaEnte = codIpaEnte;
        this.tipoOperazione = tipoOperazione;
        this.modalitaRouting = modalitaRouting;
        this.attivo = attivo;
        this.note = note;
        this.dataCreazione = dataCreazione;
        this.dataAggiornamento = dataAggiornamento;
    }

    // --- Getter e Setter ---

    @Override
    public String toString() {
        return "EnteConfig{" +
                "id=" + id +
                ", codIpaEnte='" + codIpaEnte + '\'' +
                ", tipoOperazione='" + tipoOperazione + '\'' +
                ", modalitaRouting=" + modalitaRouting +
                ", attivo=" + attivo +
                '}';
    }
}
