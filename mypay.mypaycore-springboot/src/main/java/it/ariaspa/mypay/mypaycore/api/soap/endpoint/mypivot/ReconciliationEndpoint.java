package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypivot;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient;
import it.ariaspa.mypay.mypaycore.api.logging.TransactionLoggingService;
import it.ariaspa.mypay.mypaycore.api.metrics.MiddlewareMetricsService;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecisionService;
import it.ariaspa.mypay.mypaycore.api.soap.endpoint.AbstractSoapProxyEndpoint;
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
 * <p>Operazione:
 * <ul>
 *   <li>{@code pivotSILAutorizzaImportFlussoTesoreria} — importazione flusso tesoreria</li>
 * </ul>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class ReconciliationEndpoint extends AbstractSoapProxyEndpoint {

    /** Namespace URI per le operazioni di riconciliazione (body). */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/pivot/ente/";

    /**
     * Percorso relativo dell'endpoint di riconciliazione sulla Piattaforma Unitaria.
     */
    static final String PLATFORM_PATH =
            "/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati";

    /**
     * Path di default per il fallback (quando il TransportContext non e' disponibile).
     */
    private static final String DEFAULT_PATH = "/ws/pivot";

    /**
     * Crea l'endpoint con tutte le dipendenze necessarie.
     *
     * @param piattaformaClient        client per l'inoltro verso la PU
     * @param proxyForwardingClient    client per il forward verso i backend legacy
     * @param routingDecisionService   servizio di decisione del routing
     * @param transactionLoggingService servizio per il logging transazionale
     * @param metricsService           servizio per la raccolta metriche
     * @param enteCacheService         cache degli enti con lookup duale
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

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlussoTesoreria}.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoTesoreria")
    @ResponsePayload
    public Element handleReconciliationRequest(@RequestPayload Element requestPayload,
                                               MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
