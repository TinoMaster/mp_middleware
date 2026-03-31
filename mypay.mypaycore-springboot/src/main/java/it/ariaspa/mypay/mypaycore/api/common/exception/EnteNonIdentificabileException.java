package it.ariaspa.mypay.mypaycore.api.common.exception;

/**
 * Eccezione lanciata quando non e' possibile identificare l'ente dalla richiesta SOAP.
 *
 * <p>A differenza di {@link EnteNonCensitoException} (ente noto ma non autorizzato),
 * questa eccezione indica che la richiesta del SIL e' strutturalmente incompleta:
 * manca sia il tag {@code <codIpaEnte>} sia il tag {@code <identificativoDominio>},
 * oppure il codice fiscale fornito non corrisponde ad alcun ente censito.
 *
 * <p>Si tratta di un errore del <strong>chiamante</strong> (SOAP Fault Client):
 * il SIL ha inviato un messaggio che non consente al middleware di determinare
 * a quale ente appartiene la richiesta.
 *
 * <p>Questa eccezione viene mappata dal {@code SoapFaultExceptionResolver}
 * in un SOAP Fault Client con codice {@code ENTE_NON_IDENTIFICABILE}.
 *
 * <p>Scenari tipici:
 * <ul>
 *   <li>SOAP Envelope senza {@code <codIpaEnte>} e senza {@code <identificativoDominio>}</li>
 *   <li>{@code <identificativoDominio>} presente ma il codice fiscale non corrisponde
 *       ad alcun ente in {@code mygov_ente}</li>
 * </ul>
 *
 * @see EnteNonCensitoException
 * @see it.ariaspa.mypay.mypaycore.api.soap.exception.SoapFaultExceptionResolver
 */
public class EnteNonIdentificabileException extends RuntimeException {

    /**
     * Crea una nuova eccezione per un ente non identificabile dalla richiesta.
     *
     * @param messaggio descrizione del motivo per cui l'ente non e' identificabile
     */
    public EnteNonIdentificabileException(String messaggio) {
        super(messaggio);
    }
}
