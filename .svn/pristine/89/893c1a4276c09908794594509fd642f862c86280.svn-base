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
 * Endpoint SOAP per l'operazione AvvisiDigitali — invio avviso digitale (1 operazione).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciAvvisiDigitali} di MyPay4
 * (modulo FESP). Condivide lo stesso namespace
 * {@code http://www.regione.veneto.it/pagamenti/nodoregionalefesp/} con l'endpoint RP.
 *
 * <p>Namespace body: {@code http://www.regione.veneto.it/pagamenti/nodoregionalefesp/}
 *
 * <p>Identificazione ente: header SOAP SAC con {@code <identificativoDominio>}
 * (codice fiscale, risolto a codIpaEnte tramite cache duale).
 *
 * <p>Operazioni (1):
 * <ol>
 *   <li>{@code nodoSILInviaAvvisoDigitale} — invio avviso digitale</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciAvvisiDigitaliEndpoint extends AbstractSoapProxyEndpoint {

    /**
     * Namespace URI condiviso con RPEndpoint.
     * Centralizzato in {@link Constants#NS_FESP_NODO_REGIONALE}.
     */
    static final String NAMESPACE_URI = Constants.NS_FESP_NODO_REGIONALE;

    /** Path PU per l'inoltro alla Piattaforma Unitaria (area pagamenti MyPay). */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH_PU_MYPAY;

    /** Path di default per il fallback quando il TransportContext non è disponibile. */
    private static final String DEFAULT_PATH = Constants.DEFAULT_PATH_FESP;

    public PagamentiTelematiciAvvisiDigitaliEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. nodoSILInviaAvvisoDigitale ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "nodoSILInviaAvvisoDigitale")
    @ResponsePayload
    public Element nodoSILInviaAvvisoDigitale(@RequestPayload Element request,
                                               MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }

    /**
     * Namespace del fault detail per gli endpoint MyPay FESP (area ente/pagamenti).
     */
    @Override
    public String getFaultDetailNamespace() {
        return Constants.NS_FAULT_MYPAY;
    }
}
