package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypay.fesp;

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
 * Endpoint SOAP per le operazioni RP — Richiesta di Pagamento (8 operazioni).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciRP} di MyPay4
 * (modulo FESP, nodo regionale). Include operazioni per flussi SPC, invio RP,
 * gestione IUV e richiesta RT.
 *
 * <p>Namespace body: {@code http://www.regione.veneto.it/pagamenti/nodoregionalefesp/}
 * <br>Namespace header: {@code http://www.regione.veneto.it/pagamenti/nodoregionalefesp/ppthead}
 * (solo per {@code nodoSILInviaRP})
 *
 * <p>Identificazione ente: {@code <identificativoDominio>} nell'header SOAP
 * (solo {@code nodoSILInviaRP}); per le altre operazioni ricerca generica nel body.
 *
 * <p>Operazioni (8):
 * <ol>
 *   <li>{@code chiediFlussoSPC} — richiesta flusso SPC</li>
 *   <li>{@code chiediFlussoSPCPage} — richiesta flusso SPC paginato</li>
 *   <li>{@code chiediListaFlussiSPC} — richiesta elenco flussi SPC</li>
 *   <li>{@code nodoSILChiediCopiaEsito} — richiesta copia esito</li>
 *   <li>{@code nodoSILInviaRP} — invio richiesta di pagamento (con header)</li>
 *   <li>{@code nodoSILChiediIUV} — richiesta IUV</li>
 *   <li>{@code nodoSILInviaCarrelloRP} — invio carrello di richieste di pagamento</li>
 *   <li>{@code nodoSILRichiediRT} — richiesta ricevuta telematica</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciRPEndpoint extends AbstractSoapProxyEndpoint {

    /** Namespace URI per le operazioni RP (nodo regionale FESP). */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/nodoregionalefesp/";

    /** Path PU per l'inoltro alla Piattaforma Unitaria. */
    static final String PLATFORM_PATH = "/pu/sil/soap/fesp/PagamentiTelematiciRP";

    /** Path di default per il fallback. */
    private static final String DEFAULT_PATH = "/ws/fesp";

    public PagamentiTelematiciRPEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. chiediFlussoSPC ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "chiediFlussoSPC")
    @ResponsePayload
    public Element chiediFlussoSPC(@RequestPayload Element request,
                                    MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. chiediFlussoSPCPage ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "chiediFlussoSPCPage")
    @ResponsePayload
    public Element chiediFlussoSPCPage(@RequestPayload Element request,
                                        MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 3. chiediListaFlussiSPC ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "chiediListaFlussiSPC")
    @ResponsePayload
    public Element chiediListaFlussiSPC(@RequestPayload Element request,
                                         MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 4. nodoSILChiediCopiaEsito ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "nodoSILChiediCopiaEsito")
    @ResponsePayload
    public Element nodoSILChiediCopiaEsito(@RequestPayload Element request,
                                            MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 5. nodoSILInviaRP (con header SOAP contenente identificativoDominio) ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "nodoSILInviaRP")
    @ResponsePayload
    public Element nodoSILInviaRP(@RequestPayload Element request,
                                   MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 6. nodoSILChiediIUV ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "nodoSILChiediIUV")
    @ResponsePayload
    public Element nodoSILChiediIUV(@RequestPayload Element request,
                                     MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 7. nodoSILInviaCarrelloRP ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "nodoSILInviaCarrelloRP")
    @ResponsePayload
    public Element nodoSILInviaCarrelloRP(@RequestPayload Element request,
                                           MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 8. nodoSILRichiediRT ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "nodoSILRichiediRT")
    @ResponsePayload
    public Element nodoSILRichiediRT(@RequestPayload Element request,
                                      MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
