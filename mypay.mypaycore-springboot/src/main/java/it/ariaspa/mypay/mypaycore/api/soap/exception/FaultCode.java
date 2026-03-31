package it.ariaspa.mypay.mypaycore.api.soap.exception;

/**
 * Codici di errore strutturati restituiti nel dettaglio del SOAP Fault del middleware.
 *
 * <p>Ogni costante corrisponde a un codice che viene inserito nell'elemento
 * {@code <errorCode>} del {@code <detail>} del SOAP Fault, permettendo ai SIL
 * di distinguere programmaticamente la natura dell'errore ricevuto.
 *
 * <p>Mapping codice → tipo di fault:
 * <ul>
 *   <li>{@link #ENTE_NON_AUTORIZZATO}    → SOAP Fault Client</li>
 *   <li>{@link #ENTE_NON_IDENTIFICABILE} → SOAP Fault Client</li>
 *   <li>{@link #CREDENZIALI_NON_VALIDE}  → SOAP Fault Client</li>
 *   <li>{@link #PATH_NON_RICONOSCIUTO}   → SOAP Fault Client</li>
 *   <li>{@link #AUTH_ERROR}              → SOAP Fault Server</li>
 *   <li>{@link #COMM_ERROR}              → SOAP Fault Server</li>
 *   <li>{@link #INTERNAL_ERROR}          → SOAP Fault Server</li>
 * </ul>
 *
 * @see SoapFaultExceptionResolver
 */
public enum FaultCode {

    /**
     * L'ente e' noto al middleware ma non e' autorizzato a operare.
     * Corrisponde a {@link it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException}.
     */
    ENTE_NON_AUTORIZZATO,

    /**
     * Impossibile identificare l'ente dalla richiesta SOAP.
     * Corrisponde a {@link it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonIdentificabileException}.
     */
    ENTE_NON_IDENTIFICABILE,

    /**
     * Le credenziali del SIL (codIpaEnte + password) non sono valide.
     * Corrisponde a {@link it.ariaspa.mypay.mypaycore.api.common.exception.CredenzialeSilNonValidaException}.
     */
    CREDENZIALI_NON_VALIDE,

    /**
     * Il path HTTP della richiesta non e' configurato nel middleware.
     * Corrisponde a {@link it.ariaspa.mypay.mypaycore.api.common.exception.PathNonRiconosciutoException}.
     */
    PATH_NON_RICONOSCIUTO,

    /**
     * Errore di autenticazione OAuth2 verso la Piattaforma Unitaria.
     * Corrisponde a {@link it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException}.
     */
    AUTH_ERROR,

    /**
     * Errore di comunicazione con il backend (PU o legacy).
     * Corrisponde a {@link it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException}.
     */
    COMM_ERROR,

    /**
     * Errore interno imprevisto del middleware.
     * Generato da eccezioni non mappate esplicitamente.
     */
    INTERNAL_ERROR
}
