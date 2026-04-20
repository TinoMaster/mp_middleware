package it.ariaspa.mypay.mypaycore.api.common.exception;

/**
 * Eccezione lanciata quando la comunicazione con la Piattaforma Unitaria fallisce.
 *
 * Scenari tipici:
 * - Timeout nella comunicazione HTTP
 * - Errore di rete (connessione rifiutata, DNS non risolvibile)
 * - Risposta HTTP con stato di errore (4xx, 5xx diverso da 401)
 * - Circuit breaker aperto
 */
public class PiattaformaCommunicationException extends RuntimeException {

    private final int httpStatus;

    public PiattaformaCommunicationException(String message) {
        super(message);
        this.httpStatus = 0;
    }

    public PiattaformaCommunicationException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
    }

    public PiattaformaCommunicationException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public PiattaformaCommunicationException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /**
     * Restituisce il codice di stato HTTP della risposta, o 0 se non disponibile.
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
