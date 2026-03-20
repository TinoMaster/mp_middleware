package it.ariaspa.mypay.mypaycore.api.common.exception;

/**
 * Eccezione lanciata quando un ente non e' censito nel database del middleware.
 *
 * <p>Viene sollevata dal {@code RoutingDecisionService} quando la coppia
 * {@code (codIpaEnte, tipoOperazione)} non corrisponde ad alcun record attivo
 * nella tabella {@code mwpay_ente_config}.
 *
 * <p>Questa eccezione viene mappata dal {@code SoapFaultExceptionResolver}
 * in un SOAP Fault con codice {@code ENTE_NON_AUTORIZZATO}.
 *
 * <p>Scenari tipici:
 * <ul>
 *   <li>L'ente non e' stato ancora configurato nel middleware</li>
 *   <li>L'ente esiste ma non ha una regola attiva per l'operazione richiesta</li>
 *   <li>L'ente e' stato disattivato dall'amministratore</li>
 * </ul>
 *
 * @see it.ariaspa.mypay.mypaycore.api.soap.exception.SoapFaultExceptionResolver
 */
public class EnteNonCensitoException extends RuntimeException {

    /** Codice IPA dell'ente non trovato. */
    private final String codIpaEnte;

    /** Tipo di operazione richiesta. */
    private final String tipoOperazione;

    /**
     * Crea una nuova eccezione per un ente non censito.
     *
     * @param codIpaEnte     codice IPA dell'ente non trovato
     * @param tipoOperazione tipo di operazione richiesta
     */
    public EnteNonCensitoException(String codIpaEnte, String tipoOperazione) {
        super("Ente non censito nel middleware: codIpaEnte='" + codIpaEnte
                + "', tipoOperazione='" + tipoOperazione + "'");
        this.codIpaEnte = codIpaEnte;
        this.tipoOperazione = tipoOperazione;
    }

    /**
     * Restituisce il codice IPA dell'ente non trovato.
     *
     * @return codice IPA dell'ente
     */
    public String getCodIpaEnte() {
        return codIpaEnte;
    }

    /**
     * Restituisce il tipo di operazione richiesta.
     *
     * @return tipo di operazione
     */
    public String getTipoOperazione() {
        return tipoOperazione;
    }
}
