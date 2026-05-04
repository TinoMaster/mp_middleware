package it.ariaspa.mypay.mypaycore.api.upload;

import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;

import java.time.Instant;

/**
 * DTO immutabile che rappresenta un'entry nella cache del proxy upload.
 *
 * <p>Salva le informazioni necessarie per inoltrare il file di upload
 * dal SIL al backend corretto (legacy o PU) quando il SIL chiama
 * l'endpoint REST di upload del middleware.
 *
 * <p>L'entry viene creata durante il post-processing della risposta
 * di {@code paaSILAutorizzaImportFlusso} e recuperata (one-shot)
 * quando il SIL chiama {@code POST /api/upload/flusso}.
 *
 * <p>La chiave di lookup nella cache è l'{@code authorizationToken}
 * restituito dalla risposta di {@code paaSILAutorizzaImportFlusso}.
 * Questo token JWT è generato dal backend (MyPay legacy o PU) ed è
 * univoco per ogni sessione di import.
 *
 * @see UploadProxyCacheService
 * @see UploadFlussoController
 */
public class UploadProxyEntry {

    /** URL originale di upload restituita dal backend (legacy o PU). */
    private final String uploadUrlOriginale;

    /**
     * Token JWT di autorizzazione generato dal backend per l'upload.
     * Contiene: codIpaEnte, requestToken, uploadType (codifica il tipo di import).
     * Viene inviato dal SIL come parametro al momento dell'upload.
     */
    private final String authorizationToken;

    /** Token univoco di richiesta per tracciare l'import (UUID generato dal backend). */
    private final String requestToken;

    /** Percorso relativo per l'import del flusso (es. {@code /IMPORT}). */
    private final String importPath;

    /** Modalità di routing: PIATTAFORMA_UNITARIA o LEGACY. */
    private final ModalitaRouting modalitaRouting;

    /** Codice IPA dell'ente che ha effettuato la richiesta. */
    private final String codIpaEnte;

    /**
     * Endpoint di origine della richiesta (MYPAY o MYPIVOT).
     * Usato da {@link UploadFlussoController} per decidere se applicare
     * la verifica della versione del file (solo per richieste originate da MyPay).
     */
    private final BackendDestinatario endpointOrigine;

    /** Timestamp di creazione dell'entry (usato per il calcolo del TTL). */
    private final Instant timestampCreazione;

    /**
     * Crea una nuova entry per la cache del proxy upload.
     *
     * @param uploadUrlOriginale URL originale di upload dal backend
     * @param authorizationToken token JWT di autorizzazione generato dal backend
     * @param requestToken       token univoco della richiesta (UUID)
     * @param importPath         percorso relativo per l'import del flusso
     * @param modalitaRouting    modalità di routing (PU o LEGACY)
     * @param codIpaEnte         codice IPA dell'ente
     * @param endpointOrigine    endpoint di origine della richiesta (MYPAY o MYPIVOT)
     */
    public UploadProxyEntry(String uploadUrlOriginale,
                            String authorizationToken,
                            String requestToken,
                            String importPath,
                            ModalitaRouting modalitaRouting,
                            String codIpaEnte,
                            BackendDestinatario endpointOrigine) {
        this.uploadUrlOriginale = uploadUrlOriginale;
        this.authorizationToken = authorizationToken;
        this.requestToken = requestToken;
        this.importPath = importPath;
        this.modalitaRouting = modalitaRouting;
        this.codIpaEnte = codIpaEnte;
        this.endpointOrigine = endpointOrigine;
        this.timestampCreazione = Instant.now();
    }

    /**
     * @return URL originale di upload del backend
     */
    public String getUploadUrlOriginale() {
        return uploadUrlOriginale;
    }

    /**
     * @return token JWT di autorizzazione generato dal backend
     */
    public String getAuthorizationToken() {
        return authorizationToken;
    }

    /**
     * @return token univoco della richiesta (UUID)
     */
    public String getRequestToken() {
        return requestToken;
    }

    /**
     * @return percorso relativo per l'import del flusso
     */
    public String getImportPath() {
        return importPath;
    }

    /**
     * @return modalità di routing (PU o LEGACY)
     */
    public ModalitaRouting getModalitaRouting() {
        return modalitaRouting;
    }

    /**
     * @return codice IPA dell'ente
     */
    public String getCodIpaEnte() {
        return codIpaEnte;
    }

    /**
     * @return endpoint di origine della richiesta (MYPAY o MYPIVOT)
     */
    public BackendDestinatario getEndpointOrigine() {
        return endpointOrigine;
    }

    /**
     * Verifica se la richiesta proviene dall'endpoint MyPivot.
     *
     * @return {@code true} se l'endpoint di origine è MYPIVOT
     */
    public boolean isFromMypivot() {
        return endpointOrigine == BackendDestinatario.MYPIVOT;
    }

    /**
     * @return timestamp di creazione dell'entry
     */
    public Instant getTimestampCreazione() {
        return timestampCreazione;
    }

    /**
     * Verifica se l'entry è scaduta in base al TTL specificato.
     *
     * @param ttlSecondi durata massima di validità in secondi
     * @return {@code true} se l'entry è scaduta
     */
    public boolean isScaduta(long ttlSecondi) {
        return Instant.now().isAfter(timestampCreazione.plusSeconds(ttlSecondi));
    }

    /**
     * Verifica se il routing è verso la Piattaforma Unitaria.
     *
     * @return {@code true} se la modalità di routing è PIATTAFORMA_UNITARIA
     */
    public boolean isPiattaformaUnitaria() {
        return modalitaRouting == ModalitaRouting.PIATTAFORMA_UNITARIA;
    }

    /**
     * Rappresentazione stringa dell'entry per il logging.
     * uploadUrlOriginale e authorizationToken sono esclusi per sicurezza.
     */
    @Override
    public String toString() {
        return "UploadProxyEntry{" +
                "codIpaEnte='" + codIpaEnte + '\'' +
                ", modalitaRouting=" + modalitaRouting +
                ", endpointOrigine=" + endpointOrigine +
                ", requestToken='" + requestToken + '\'' +
                ", importPath='" + importPath + '\'' +
                ", timestampCreazione=" + timestampCreazione +
                '}';
    }
}
