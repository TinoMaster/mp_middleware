package it.ariaspa.mypay.mypaycore.api.common.exception;

/**
 * Eccezione lanciata quando un ente non e' censito nel database condiviso.
 *
 * <p>Viene sollevata dal {@code RoutingDecisionService} quando il {@code codIpaEnte}
 * estratto dall'Header SOAP non corrisponde ad alcun record nella tabella
 * {@code mygov_ente}.
 *
 * <p>Questa eccezione viene mappata dal {@code SoapFaultExceptionResolver}
 * in un SOAP Fault con codice {@code ENTE_NON_AUTORIZZATO}.
 *
 * <p>Scenari tipici:
 * <ul>
 *   <li>L'ente non e' censito in {@code mygov_ente}</li>
 *   <li>Il codice IPA e' errato o non esiste</li>
 * </ul>
 *
 * @see it.ariaspa.mypay.mypaycore.api.soap.exception.SoapFaultExceptionResolver
 */
public class EnteNonCensitoException extends RuntimeException {

    /** Codice IPA dell'ente non trovato. */
    private final String codIpaEnte;

    /**
     * Crea una nuova eccezione per un ente non censito.
     *
     * @param codIpaEnte codice IPA dell'ente non trovato in {@code mygov_ente}
     */
    public EnteNonCensitoException(String codIpaEnte) {
        super("Ente non censito nel sistema: codIpaEnte='" + codIpaEnte + "'");
        this.codIpaEnte = codIpaEnte;
    }

    /**
     * Restituisce il codice IPA dell'ente non trovato.
     *
     * @return codice IPA dell'ente
     */
    public String getCodIpaEnte() {
        return codIpaEnte;
    }
}
