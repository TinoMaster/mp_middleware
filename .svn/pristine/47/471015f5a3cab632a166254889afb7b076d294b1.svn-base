package it.ariaspa.mypay.mypaycore.api.common.exception;

/**
 * Eccezione lanciata quando il path della richiesta SOAP non corrisponde
 * a nessun backend registrato nel {@code PathRegistryConfig}.
 *
 * <p>Viene sollevata dal {@code RoutingDecisionService} quando il path HTTP
 * della richiesta non e' mappato a nessun backend di destinazione
 * (ne' MYPAY ne' MYPIVOT).
 *
 * <p>Questa eccezione viene mappata dal {@code SoapFaultExceptionResolver}
 * in un SOAP Fault con codice {@code PATH_NON_RICONOSCIUTO}.
 *
 * <p>Scenari tipici:
 * <ul>
 *   <li>Il SIL ha inviato la richiesta a un path non configurato nel middleware</li>
 *   <li>Il path e' stato rimosso dalla configurazione</li>
 * </ul>
 *
 * @see it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig
 * @see it.ariaspa.mypay.mypaycore.api.soap.exception.SoapFaultExceptionResolver
 */
public class PathNonRiconosciutoException extends RuntimeException {

    /** Path HTTP della richiesta non riconosciuto. */
    private final String requestPath;

    /**
     * Crea una nuova eccezione per un path non riconosciuto.
     *
     * @param requestPath il path HTTP della richiesta non mappato
     */
    public PathNonRiconosciutoException(String requestPath) {
        super("Path non riconosciuto nel middleware: '" + requestPath
                + "'. Nessun backend di destinazione configurato per questo path.");
        this.requestPath = requestPath;
    }

    /**
     * Restituisce il path della richiesta non riconosciuto.
     *
     * @return il path HTTP
     */
    public String getRequestPath() {
        return requestPath;
    }
}
