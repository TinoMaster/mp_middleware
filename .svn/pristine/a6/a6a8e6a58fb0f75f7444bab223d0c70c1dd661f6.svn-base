package it.ariaspa.mypay.mypaycore.api.common.exception;

import lombok.Getter;

/**
 * Eccezione lanciata quando la password fornita dal SIL nella richiesta SOAP
 * non corrisponde a quella configurata per l'ente in {@code mygov_ente.de_password}.
 *
 * <p>Viene sollevata da {@code AbstractSoapProxyEndpoint} dopo aver identificato
 * l'ente (il {@code codIpaEnte} esiste in {@code mygov_ente}) ma aver verificato
 * che la password inviata nel tag {@code <password>} del body SOAP non coincide
 * con quella attesa, oppure che il tag {@code <password>} e' assente nella richiesta.
 *
 * <p>Il campo {@code de_password} e' obbligatorio in {@code mygov_ente}:
 * ogni ente deve avere una password configurata.
 *
 * <p>Questa eccezione viene mappata dal {@code SoapFaultExceptionResolver}
 * in un SOAP Fault Client con codice {@code CREDENZIALI_NON_VALIDE}.
 * Il messaggio di fault non rivela se il problema e' l'ente o la password,
 * per non facilitare attacchi di enumerazione.
 *
 * @see it.ariaspa.mypay.mypaycore.api.soap.exception.SoapFaultExceptionResolver
 */
@Getter
public class CredenzialeSilNonValidaException extends RuntimeException {

    /** Codice IPA dell'ente per cui la verifica delle credenziali e' fallita.
     * -- GETTER --
     *  Restituisce il codice IPA dell'ente per cui la verifica e' fallita.
     *
     */
    private final String codIpaEnte;

    /**
     * Crea una nuova eccezione per credenziali SIL non valide.
     *
     * @param codIpaEnte codice IPA dell'ente per cui la password non e' corretta
     */
    public CredenzialeSilNonValidaException(String codIpaEnte) {
        // Messaggio interno dettagliato (solo per log), non esposto al SIL nel fault
        super("Credenziali SIL non valide per l'ente: codIpaEnte='" + codIpaEnte + "'");
        this.codIpaEnte = codIpaEnte;
    }

}
