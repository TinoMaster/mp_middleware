package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypay.fesp;

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
 * Endpoint SOAP per le operazioni CCP25 — pagoPA SANP 2.5 (5 operazioni).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciCCP25} di MyPay4
 * (modulo FESP). Queste operazioni seguono il protocollo SANP 2.5 della PagoPA.
 *
 * <p>Namespace body: {@code http://pagopa-api.pagopa.gov.it/pa/paForNode.xsd}
 *
 * <p>Identificazione ente: queste operazioni NON hanno header SOAP.
 * L'ente viene identificato tramite ricerca generica nel body SOAP
 * (tipicamente {@code <idPA>} o {@code <identificativoDominio>}).
 *
 * <p>Operazioni (5):
 * <ol>
 *   <li>{@code paVerifyPaymentNoticeReq} — verifica avviso di pagamento</li>
 *   <li>{@code paGetPaymentReq} — recupero dati pagamento</li>
 *   <li>{@code paSendRTReq} — invio ricevuta telematica</li>
 *   <li>{@code paSendRTV2Request} — invio RT v2</li>
 *   <li>{@code paGetPaymentV2Request} — recupero dati pagamento v2</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciCCP25Endpoint extends AbstractSoapProxyEndpoint {

    /** Namespace URI per le operazioni CCP25 (SANP 2.5). */
    static final String NAMESPACE_URI = "http://pagopa-api.pagopa.gov.it/pa/paForNode.xsd";

    /** Path PU per l'inoltro alla Piattaforma Unitaria. */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH;

    /** Path di default per il fallback. */
    private static final String DEFAULT_PATH = "/ws/fesp";

    public PagamentiTelematiciCCP25Endpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. paVerifyPaymentNoticeReq ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paVerifyPaymentNoticeReq")
    @ResponsePayload
    public Element paVerifyPaymentNoticeReq(@RequestPayload Element request,
                                             MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. paGetPaymentReq ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paGetPaymentReq")
    @ResponsePayload
    public Element paGetPaymentReq(@RequestPayload Element request,
                                    MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 3. paSendRTReq ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paSendRTReq")
    @ResponsePayload
    public Element paSendRTReq(@RequestPayload Element request,
                                MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 4. paSendRTV2Request ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paSendRTV2Request")
    @ResponsePayload
    public Element paSendRTV2Request(@RequestPayload Element request,
                                      MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 5. paGetPaymentV2Request ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paGetPaymentV2Request")
    @ResponsePayload
    public Element paGetPaymentV2Request(@RequestPayload Element request,
                                          MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
