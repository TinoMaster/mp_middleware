package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypivot;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient;
import it.ariaspa.mypay.mypaycore.api.logging.TransactionLoggingService;
import it.ariaspa.mypay.mypaycore.api.metrics.MiddlewareMetricsService;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecisionService;
import it.ariaspa.mypay.mypaycore.api.soap.endpoint.AbstractSoapProxyEndpoint;
import it.ariaspa.mypay.mypaycore.api.util.Constants;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.w3c.dom.Element;

/**
 * Endpoint SOAP per la riconciliazione dei pagamenti telematici (mypivot).
 *
 * <p>Riceve le richieste SOAP dai sistemi SIL (Sistemi Informativi Locali)
 * sul path {@code /ws/pivot/PagamentiTelematiciPagatiRiconciliati} e le instrada
 * verso il backend corretto in base alla configurazione dell'ente nel database.
 *
 * <p>Namespace:
 * <ul>
 *   <li>Header: {@code http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead}</li>
 *   <li>Body: {@code http://www.regione.veneto.it/pagamenti/pivot/ente/}</li>
 * </ul>
 *
 * <p>Operazioni esposte (10 in totale — contratto WSDL {@code mypivot-per-ente.wsdl}):
 * <ul>
 *   <li>{@code pivotSILAutorizzaImportFlusso} — autorizza importazione flusso generico</li>
 *   <li>{@code pivotSILAutorizzaImportFlussoRendicontazione} — autorizza importazione flusso rendicontazione</li>
 *   <li>{@code pivotSILAutorizzaImportFlussoRT} — autorizza importazione flusso RT</li>
 *   <li>{@code pivotSILAutorizzaImportFlussoTesoreria} — autorizza importazione flusso tesoreria</li>
 *   <li>{@code pivotSILChiediAccertamento} — richiede dati di accertamento</li>
 *   <li>{@code pivotSILChiediPagatiRiconciliati} — richiede pagati riconciliati</li>
 *   <li>{@code pivotSILChiediStatoExportFlussoRiconciliazione} — stato export flusso riconciliazione</li>
 *   <li>{@code pivotSILChiediStatoImportFlusso} — stato importazione flusso generico</li>
 *   <li>{@code pivotSILChiediStatoImportFlussoTesoreria} — stato importazione flusso tesoreria</li>
 *   <li>{@code pivotSILPrenotaExportFlussoRiconciliazione} — prenota export flusso riconciliazione</li>
 * </ul>
 *
 * <p>Tutte le operazioni vengono inoltrate al medesimo path sulla Piattaforma Unitaria:
 * {@code /pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati}
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class ReconciliationEndpoint extends AbstractSoapProxyEndpoint {

    /** Namespace URI per le operazioni di riconciliazione (body). */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/pivot/ente/";

    /**
     * Percorso relativo dell'endpoint di riconciliazione sulla Piattaforma Unitaria.
     * Tutte le 10 operazioni di questo endpoint condividono lo stesso path PU.
     */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH;

    /**
     * Path di default per il fallback (quando il TransportContext non e' disponibile).
     */
    private static final String DEFAULT_PATH = "/ws/pivot";

    /**
     * Crea l'endpoint con tutte le dipendenze necessarie.
     *
     * @param piattaformaClient         client per l'inoltro verso la PU
     * @param proxyForwardingClient     client per il forward verso i backend legacy
     * @param routingDecisionService    servizio di decisione del routing
     * @param transactionLoggingService servizio per il logging transazionale
     * @param metricsService            servizio per la raccolta metriche
     * @param enteCacheService          cache degli enti con lookup duale
     */
    public ReconciliationEndpoint(PiattaformaUnitariaClient piattaformaClient,
                                  ProxyForwardingClient proxyForwardingClient,
                                  RoutingDecisionService routingDecisionService,
                                  TransactionLoggingService transactionLoggingService,
                                  MiddlewareMetricsService metricsService,
                                  EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // -------------------------------------------------------------------------
    // Operazioni di autorizzazione importazione flussi
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlusso}.
     * Autorizza l'importazione di un flusso generico da parte del SIL.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlusso")
    @ResponsePayload
    public Element handleAutorizzaImportFlusso(@RequestPayload Element requestPayload,
                                               MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlussoRendicontazione}.
     * Autorizza l'importazione di un flusso di rendicontazione da parte del SIL.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoRendicontazione")
    @ResponsePayload
    public Element handleAutorizzaImportFlussoRendicontazione(@RequestPayload Element requestPayload,
                                                              MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlussoRT}.
     * Autorizza l'importazione di un flusso di Ricevute Telematiche (RT) da parte del SIL.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoRT")
    @ResponsePayload
    public Element handleAutorizzaImportFlussoRT(@RequestPayload Element requestPayload,
                                                 MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlussoTesoreria}.
     * Autorizza l'importazione di un flusso di tesoreria da parte del SIL.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoTesoreria")
    @ResponsePayload
    public Element handleAutorizzaImportFlussoTesoreria(@RequestPayload Element requestPayload,
                                                        MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    // -------------------------------------------------------------------------
    // Operazioni di interrogazione stato importazione flussi
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediStatoImportFlusso}.
     * Interroga lo stato di una precedente operazione di importazione flusso generico.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediStatoImportFlusso")
    @ResponsePayload
    public Element handleChiediStatoImportFlusso(@RequestPayload Element requestPayload,
                                                 MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediStatoImportFlussoTesoreria}.
     * Interroga lo stato di una precedente operazione di importazione flusso tesoreria.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediStatoImportFlussoTesoreria")
    @ResponsePayload
    public Element handleChiediStatoImportFlussoTesoreria(@RequestPayload Element requestPayload,
                                                          MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    // -------------------------------------------------------------------------
    // Operazioni di export e riconciliazione
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILPrenotaExportFlussoRiconciliazione}.
     * Prenota la generazione di un flusso di export per la riconciliazione.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILPrenotaExportFlussoRiconciliazione")
    @ResponsePayload
    public Element handlePrenotaExportFlussoRiconciliazione(@RequestPayload Element requestPayload,
                                                            MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediStatoExportFlussoRiconciliazione}.
     * Interroga lo stato di una precedente prenotazione di export flusso riconciliazione.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediStatoExportFlussoRiconciliazione")
    @ResponsePayload
    public Element handleChiediStatoExportFlussoRiconciliazione(@RequestPayload Element requestPayload,
                                                                MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    // -------------------------------------------------------------------------
    // Operazioni di interrogazione pagati e accertamento
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediPagatiRiconciliati}.
     * Recupera i pagamenti riconciliati per un ente, filtrabili per IUV/IUF o per data.
     *
     * <p>Nota: questa operazione non richiede header {@code intestazionePPT} nel contratto
     * WSDL originale; l'identificazione dell'ente avviene tramite {@code codIpaEnte}
     * presente nel body della richiesta.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediPagatiRiconciliati")
    @ResponsePayload
    public Element handleChiediPagatiRiconciliati(@RequestPayload Element requestPayload,
                                                  MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediAccertamento}.
     * Recupera i dati di accertamento per un ente, ricercabili per bolletta o per IUF.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediAccertamento")
    @ResponsePayload
    public Element handleChiediAccertamento(@RequestPayload Element requestPayload,
                                            MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
