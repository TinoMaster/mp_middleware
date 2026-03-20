package it.ariaspa.mypay.mypaycore.api.domain;

/**
 * Enum che rappresenta la modalita di instradamento di una richiesta SOAP.
 *
 * <p>Determina come il middleware gestisce la richiesta per un determinato ente
 * e tipo di operazione:
 * <ul>
 *   <li>{@link #PIATTAFORMA_UNITARIA} — inoltro con autenticazione OAuth2 verso la
 *       Piattaforma Unitaria di pagoPA</li>
 *   <li>{@link #LEGACY} — forward diretto (trasparente) verso il backend legacy
 *       (mypay o mypivot), senza autenticazione aggiuntiva</li>
 * </ul>
 *
 * <p>Il valore viene letto dalla colonna {@code modalita_routing} della tabella
 * {@code mwpay_ente_config} nel database.
 *
 * @see EnteConfig
 */
public enum ModalitaRouting {

    /**
     * Inoltra la richiesta alla Piattaforma Unitaria di pagoPA con autenticazione OAuth2.
     * Il middleware aggiunge automaticamente il token Bearer alla richiesta.
     */
    PIATTAFORMA_UNITARIA,

    /**
     * Inoltra la richiesta direttamente al backend legacy (mypay o mypivot)
     * senza autenticazione aggiuntiva. Le credenziali del SIL ({@code codIpaEnte}
     * e {@code password}) viaggiano as-is nel body SOAP.
     */
    LEGACY
}
