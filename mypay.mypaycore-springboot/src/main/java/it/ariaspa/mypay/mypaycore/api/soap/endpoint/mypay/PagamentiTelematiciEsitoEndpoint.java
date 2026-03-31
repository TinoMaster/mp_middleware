package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypay;

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
 * Endpoint SOAP per l'operazione Esito — invio esito pagamento (1 operazione).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciEsito} di MyPay4.
 * Condivide lo stesso namespace {@code http://www.regione.veneto.it/pagamenti/pa/}
 * con CCPPa e FlussiSPC, ma rappresenta un servizio logicamente distinto.
 *
 * <p>Namespace body: {@code http://www.regione.veneto.it/pagamenti/pa/}
 * <br>Namespace header: {@code http://www.regione.veneto.it/pagamenti/pa/ppthead}
 *
 * <p>Identificazione ente: {@code <identificativoDominio>} nell'header SOAP
 * (codice fiscale, risolto a codIpaEnte tramite cache duale).
 *
 * <p>Operazioni (1):
 * <ol>
 *   <li>{@code paaSILInviaEsito} — invio esito del pagamento al SIL</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciEsitoEndpoint extends AbstractSoapProxyEndpoint {

    /**
     * Namespace URI condiviso con CCPPa e FlussiSPC.
     * Centralizzato in {@link Constants#NS_MYPAY_PA}.
     */
    static final String NAMESPACE_URI = Constants.NS_MYPAY_PA;

    /** Path PU per l'inoltro alla Piattaforma Unitaria (area pagamenti MyPay). */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH_PU_MYPAY;

    /** Path di default per il fallback quando il TransportContext non è disponibile. */
    private static final String DEFAULT_PATH = Constants.DEFAULT_PATH_PA;

    public PagamentiTelematiciEsitoEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. paaSILInviaEsito ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILInviaEsito")
    @ResponsePayload
    public Element paaSILInviaEsito(@RequestPayload Element request,
                                     MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
