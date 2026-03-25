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
 * Endpoint SOAP per le operazioni CCP — Contesto di Pagamento (2 operazioni).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciCCP} di MyPay4 (modulo FESP).
 * Riceve le richieste SOAP dai SIL sul path {@code /ws/fesp/} e le instrada
 * verso la PU o il backend legacy.
 *
 * <p>Namespace body: {@code http://ws.pagamenti.telematici.gov/}
 * <br>Namespace header: {@code http://ws.pagamenti.telematici.gov/ppthead}
 *
 * <p>Identificazione ente: {@code <identificativoDominio>} nell'header SOAP
 * ({@code intestazionePPT} del namespace {@code gov/ppthead}).
 *
 * <p>Operazioni (2):
 * <ol>
 *   <li>{@code paaVerificaRPT} — verifica RPT</li>
 *   <li>{@code paaAttivaRPT} — attivazione RPT</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciCCPEndpoint extends AbstractSoapProxyEndpoint {

    /** Namespace URI per le operazioni CCP. */
    static final String NAMESPACE_URI = "http://ws.pagamenti.telematici.gov/";

    /** Path PU per l'inoltro alla Piattaforma Unitaria. */
    static final String PLATFORM_PATH = "/pu/sil/soap/fesp/PagamentiTelematiciCCP";

    /** Path di default per il fallback. */
    private static final String DEFAULT_PATH = "/ws/fesp";

    public PagamentiTelematiciCCPEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. paaVerificaRPT ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaVerificaRPT")
    @ResponsePayload
    public Element paaVerificaRPT(@RequestPayload Element request,
                                   MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. paaAttivaRPT ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaAttivaRPT")
    @ResponsePayload
    public Element paaAttivaRPT(@RequestPayload Element request,
                                 MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
