package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypay;

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
 * Endpoint SOAP per le operazioni FlussiSPC — flussi rendicontazione SPC (2 operazioni).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciFlussiSPC} di MyPay4.
 * Condivide lo stesso namespace {@code http://www.regione.veneto.it/pagamenti/pa/}
 * con CCPPa e Esito.
 *
 * <p>Namespace body: {@code http://www.regione.veneto.it/pagamenti/pa/}
 *
 * <p>Identificazione ente: queste operazioni NON hanno un header SOAP specifico.
 * L'ente viene identificato tramite ricerca generica nel body SOAP
 * ({@code <codIpaEnte>} o {@code <identificativoDominio>}).
 *
 * <p>Operazioni (2):
 * <ol>
 *   <li>{@code paaSILChiediFlussoSPC} — richiesta singolo flusso SPC</li>
 *   <li>{@code paaSILChiediElencoFlussiSPC} — richiesta elenco flussi SPC</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciFlussiSPCEndpoint extends AbstractSoapProxyEndpoint {

    /** Namespace URI (condiviso con CCPPa e Esito). */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/pa/";

    /** Path PU per l'inoltro alla Piattaforma Unitaria. */
    static final String PLATFORM_PATH = "/pu/sil/soap/pa/PagamentiTelematiciFlussiSPC";

    /** Path di default per il fallback. */
    private static final String DEFAULT_PATH = "/ws/pa";

    public PagamentiTelematiciFlussiSPCEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. paaSILChiediFlussoSPC ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediFlussoSPC")
    @ResponsePayload
    public Element paaSILChiediFlussoSPC(@RequestPayload Element request,
                                          MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. paaSILChiediElencoFlussiSPC ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediElencoFlussiSPC")
    @ResponsePayload
    public Element paaSILChiediElencoFlussiSPC(@RequestPayload Element request,
                                                MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
