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
 * Endpoint SOAP per l'operazione RT — Ricevuta Telematica (1 operazione).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciRT} di MyPay4
 * (modulo FESP). Condivide lo stesso namespace {@code http://ws.pagamenti.telematici.gov/}
 * con l'endpoint CCP.
 *
 * <p>Namespace body: {@code http://ws.pagamenti.telematici.gov/}
 * <br>Namespace header: {@code http://ws.pagamenti.telematici.gov/ppthead}
 *
 * <p>Identificazione ente: {@code <identificativoDominio>} nell'header SOAP.
 *
 * <p>Operazioni (1):
 * <ol>
 *   <li>{@code paaInviaRT} — invio ricevuta telematica al PA</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciRTEndpoint extends AbstractSoapProxyEndpoint {

    /** Namespace URI (condiviso con CCP). */
    static final String NAMESPACE_URI = "http://ws.pagamenti.telematici.gov/";

    /** Path PU per l'inoltro alla Piattaforma Unitaria. */
    static final String PLATFORM_PATH = "/pu/sil/soap/fesp/PagamentiTelematiciRT";

    /** Path di default per il fallback. */
    private static final String DEFAULT_PATH = "/ws/fesp";

    public PagamentiTelematiciRTEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. paaInviaRT ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaInviaRT")
    @ResponsePayload
    public Element paaInviaRT(@RequestPayload Element request,
                               MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
