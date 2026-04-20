package it.ariaspa.mypay.mypaycore.api.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Modello di dominio che rappresenta la configurazione OAuth2 di un ente
 * verso la Piattaforma Unitaria (pagoPA).
 *
 * <p>Mappa la tabella {@code mygov_ente_config_pu}. La presenza di un record
 * attivo ({@code attivo = TRUE}) per un dato {@code codiceIpaEnte} indica che
 * le richieste di quell'ente devono essere inoltrate alla Piattaforma Unitaria
 * tramite autenticazione OAuth2 Client Credentials.
 *
 * <p>Se il record e' assente o ha {@code attivo = FALSE}, il middleware
 * utilizza il flusso legacy (forward diretto al backend).
 *
 * @see EnteCompleto
 */
@Getter
@Setter
public class EnteConfigPu {

    /** Identificativo univoco del record (chiave surrogata). */
    private Long id;

    /**
     * Codice IPA dell'ente — chiave esterna verso {@code mygov_ente.cod_ipa_ente}.
     */
    private String codiceIpaEnte;

    /**
     * Client ID OAuth2 assegnato dalla Piattaforma Unitaria per questo ente.
     * Viene usato nel Client Credentials Flow per ottenere il token Bearer.
     */
    private String clientId;

    /**
     * Client Secret OAuth2 associato al client ID.
     * Attualmente memorizzato in testo in chiaro — da cifrare con Jasypt in futuro.
     */
    private String clientSecret;

    /**
     * Flag di attivazione della configurazione PU.
     * Se {@code false}, il middleware usa il flusso legacy anche se il record esiste.
     */
    private boolean attivo;

    /** Data e ora di creazione del record. */
    private OffsetDateTime dtCreazione;

    /** Data e ora dell'ultimo aggiornamento del record. */
    private OffsetDateTime dtUltimaModifica;

    /**
     * Costruttore vuoto per compatibilita' con i framework di mapping (Jdbi).
     */
    public EnteConfigPu() {
    }

    @Override
    public String toString() {
        return "EnteConfigPu{" +
                "codiceIpaEnte='" + codiceIpaEnte + '\'' +
                ", clientId='" + clientId + '\'' +
                ", attivo=" + attivo +
                '}';
    }
}
