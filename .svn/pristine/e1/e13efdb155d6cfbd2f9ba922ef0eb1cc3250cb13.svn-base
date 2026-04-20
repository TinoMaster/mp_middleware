package it.ariaspa.mypay.mypaycore.api.common.exception;

/**
 * Eccezione lanciata quando l'autenticazione OAuth2 verso la Piattaforma Unitaria fallisce.
 *
 * Scenari tipici:
 * - Credenziali OAuth2 non valide (client_id / client_secret)
 * - Endpoint di autenticazione non raggiungibile
 * - Risposta dell'endpoint non valida o senza access_token
 * - Timeout nella richiesta del token
 */
public class PiattaformaAuthenticationException extends RuntimeException {

    public PiattaformaAuthenticationException(String message) {
        super(message);
    }

    public PiattaformaAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
