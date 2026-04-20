package it.ariaspa.mypay.mypaycore.api.util;

/**
 * Costanti globali del middleware mypay.mypaycore.
 *
 * <p>Centralizza tutti i valori letterali condivisi tra più componenti
 * (endpoint SOAP, client, configurazioni) per evitarne la duplicazione
 * e facilitare la manutenzione.
 *
 * <p>Convenzioni di nomenclatura:
 * <ul>
 *   <li>{@code NS_*}    — Namespace URI dei contratti WSDL/SOAP</li>
 *   <li>{@code PATH_*}  — Path HTTP esposti dal middleware ai SIL (path di fallback)</li>
 *   <li>{@code PLATFORM_PATH_PU_*} — Path sulla Piattaforma Unitaria (pagoPA),
 *       differenziati per area funzionale (MyPay / MyPivot)</li>
 * </ul>
 */
public final class Constants {

    /**
     * Costruttore privato: classe di sola utility, non istanziabile.
     */
    private Constants() {
        throw new UnsupportedOperationException("Classe di sole costanti, non istanziabile.");
    }

    // =========================================================================
    // Path sulla Piattaforma Unitaria (pagoPA) — differenziati per area funzionale
    // =========================================================================

    /**
     * Path relativo del servizio pagamenti (MyPay) sulla Piattaforma Unitaria.
     *
     * <p>Utilizzato da tutti i 9 endpoint MyPay (PA + FESP) per l'inoltro delle
     * richieste SOAP verso pagoPA.
     */
    public static final String PLATFORM_PATH_PU_MYPAY =
            "/pu/sil/soap/payments/PagamentiTelematiciDovutiPagati";

    /**
     * Path relativo del servizio di riconciliazione (MyPivot) sulla Piattaforma Unitaria.
     *
     * <p>Utilizzato dall'endpoint {@code ReconciliationEndpoint} per l'inoltro delle
     * richieste SOAP di riconciliazione verso pagoPA.
     */
    public static final String PLATFORM_PATH_PU_MYPIVOT =
            "/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati";

    // =========================================================================
    // Path di fallback esposti dal middleware (DEFAULT_PATH degli endpoint)
    //
    // Usati come fallback da AbstractSoapProxyEndpoint.extractRequestPath()
    // quando il TransportContext Spring WS non è disponibile (es. nei test unitari).
    // Ogni valore corrisponde al prefisso del path HTTP su cui il middleware espone
    // i servizi SOAP ai SIL.
    // =========================================================================

    /**
     * Path di fallback per gli endpoint MyPay lato PA (Pubblica Amministrazione).
     * Usato da: DovutiPagati, Esito, FlussiSPC, CCPPa.
     * Prefisso URL esposto ai SIL: {@code /ws/pa/...}
     */
    public static final String DEFAULT_PATH_PA = "/ws/pa";

    /**
     * Path di fallback per gli endpoint MyPay lato FESP (Front-End dei Servizi di Pagamento).
     * Usato da: CCP, CCP25, RP, RT, AvvisiDigitali.
     * Prefisso URL esposto ai SIL: {@code /ws/fesp/...}
     */
    public static final String DEFAULT_PATH_FESP = "/ws/fesp";

    /**
     * Path di fallback per l'endpoint MyPivot (riconciliazione pagamenti).
     * Usato da: ReconciliationEndpoint.
     * Prefisso URL esposto ai SIL: {@code /ws/pivot/...}
     */
    public static final String DEFAULT_PATH_PIVOT = "/ws/pivot";

    // =========================================================================
    // Namespace URI dei contratti WSDL/SOAP — condivisi tra più endpoint
    //
    // I namespace univoci (usati da un solo endpoint) sono dichiarati localmente
    // nell'endpoint stesso e non duplicati qui.
    // =========================================================================

    /**
     * Namespace URI per le operazioni del servizio MyPay lato PA.
     * Condiviso tra: EsitoEndpoint, FlussiSPCEndpoint, CCPPaEndpoint.
     * Corrisponde al namespace del body SOAP dei contratti WSDL MyPay PA.
     */
    public static final String NS_MYPAY_PA = "http://www.regione.veneto.it/pagamenti/pa/";

    /**
     * Namespace URI per le operazioni del nodo regionale FESP.
     * Condiviso tra: RPEndpoint, AvvisiDigitaliEndpoint.
     * Corrisponde al namespace del body SOAP dei contratti WSDL MyPay FESP nodo regionale.
     */
    public static final String NS_FESP_NODO_REGIONALE =
            "http://www.regione.veneto.it/pagamenti/nodoregionalefesp/";

    /**
     * Namespace URI per le operazioni del gateway di pagamento telematico (gov).
     * Condiviso tra: CCPEndpoint, RTEndpoint.
     * Corrisponde al namespace del body SOAP dei contratti WSDL NodoSPC/PagoPA legacy.
     */
    public static final String NS_PAGAMENTI_TELEMATICI_GOV =
            "http://ws.pagamenti.telematici.gov/";

    // =========================================================================
    // Namespace URI per gli elementi fault del SOAP Detail — differenziati per area
    //
    // I namespace del fault detail devono corrispondere al dominio semantico
    // dell'endpoint che genera l'errore, per rispettare i contratti WSDL originali.
    // =========================================================================

    /**
     * Namespace per gli elementi fault degli endpoint MyPay (PA + FESP, 9 endpoint).
     * Usato da: DovutiPagati, Esito, FlussiSPC, CCPPa, CCP, CCP25, RP, RT, AvvisiDigitali.
     */
    public static final String NS_FAULT_MYPAY =
            "http://www.regione.veneto.it/pagamenti/ente/fault";

    /**
     * Namespace per gli elementi fault dell'endpoint MyPivot (riconciliazione, 1 endpoint).
     * Usato da: ReconciliationEndpoint.
     */
    public static final String NS_FAULT_MYPIVOT =
            "http://www.regione.veneto.it/pagamenti/pivot/ente/fault";
}
