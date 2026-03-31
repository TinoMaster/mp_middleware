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
 * Endpoint SOAP per le operazioni CCPPa — Pagamenti Telematici CCP lato PA (4 operazioni).
 *
 * <p>Proxy trasparente per il servizio {@code PagamentiTelematiciCCPPa} di MyPay4.
 * Riceve le richieste SOAP dai SIL sul path {@code /ws/pa/} e le instrada
 * verso la PU o il backend legacy.
 *
 * <p>Namespace body: {@code http://www.regione.veneto.it/pagamenti/pa/}
 * <br>Namespace header: {@code http://www.regione.veneto.it/pagamenti/pa/ppthead}
 *
 * <p>Identificazione ente: {@code <identificativoDominio>} nell'header SOAP
 * (codice fiscale dell'ente, risolto a codIpaEnte tramite cache duale).
 *
 * <p>Operazioni (4):
 * <ol>
 *   <li>{@code paaSILAttivaRP} — attivazione richiesta di pagamento</li>
 *   <li>{@code paaSILVerificaRP} — verifica richiesta di pagamento</li>
 *   <li>{@code paVerifyPaymentNotice} — verifica avviso di pagamento (SANP 2.4)</li>
 *   <li>{@code paGetPayment} — recupero dati pagamento (SANP 2.4)</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciCCPPaEndpoint extends AbstractSoapProxyEndpoint {

    /**
     * Namespace URI per le operazioni CCPPa.
     * Condiviso con Esito e FlussiSPC — centralizzato in {@link Constants#NS_MYPAY_PA}.
     */
    static final String NAMESPACE_URI = Constants.NS_MYPAY_PA;

    /** Path PU per l'inoltro delle richieste alla Piattaforma Unitaria (area pagamenti MyPay). */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH_PU_MYPAY;

    /** Path di default per il fallback quando il TransportContext non è disponibile. */
    private static final String DEFAULT_PATH = Constants.DEFAULT_PATH_PA;

    public PagamentiTelematiciCCPPaEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. paaSILAttivaRP ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILAttivaRP")
    @ResponsePayload
    public Element paaSILAttivaRP(@RequestPayload Element request,
                                   MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. paaSILVerificaRP ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILVerificaRP")
    @ResponsePayload
    public Element paaSILVerificaRP(@RequestPayload Element request,
                                     MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 3. paVerifyPaymentNotice (SANP 2.4) ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paVerifyPaymentNotice")
    @ResponsePayload
    public Element paVerifyPaymentNotice(@RequestPayload Element request,
                                          MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 4. paGetPayment (SANP 2.4) ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paGetPayment")
    @ResponsePayload
    public Element paGetPayment(@RequestPayload Element request,
                                 MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
