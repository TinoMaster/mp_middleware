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
 * Endpoint SOAP per le operazioni DovutiPagati (16 operazioni).
 *
 * <p>Proxy trasparente per tutte le operazioni del servizio
 * {@code PagamentiTelematiciDovutiPagati} di MyPay4. Riceve le richieste SOAP
 * dai SIL sul path {@code /ws/pa/PagamentiTelematiciDovutiPagati} e le instrada
 * verso la PU (con OAuth2) o il backend legacy.
 *
 * <p>Namespace body: {@code http://www.regione.veneto.it/pagamenti/ente/}
 * <br>Namespace header: {@code http://www.regione.veneto.it/pagamenti/ente/ppthead}
 *
 * <p>Identificazione ente: {@code <codIpaEnte>} nell'header SOAP
 * ({@code intestazionePPT} del namespace {@code ente/ppthead}).
 *
 * <p>Operazioni (16):
 * <ol>
 *   <li>{@code paaSILImportaDovuto}</li>
 *   <li>{@code paaSILAutorizzaImportFlusso}</li>
 *   <li>{@code paaSILChiediEsitoCarrelloDovuti}</li>
 *   <li>{@code paaSILChiediPagati}</li>
 *   <li>{@code paaSILChiediPagatiConRicevuta}</li>
 *   <li>{@code paaSILChiediPosizioniAperte}</li>
 *   <li>{@code paaSILChiediStatoExportFlusso}</li>
 *   <li>{@code paaSILChiediStatoImportFlusso}</li>
 *   <li>{@code paaSILChiediStoricoPagamenti}</li>
 *   <li>{@code paaSILInviaDovuti}</li>
 *   <li>{@code paaSILInviaCarrelloDovuti}</li>
 *   <li>{@code paaSILPrenotaExportFlusso}</li>
 *   <li>{@code paaSILPrenotaExportFlussoIncrementaleConRicevuta}</li>
 *   <li>{@code paaSILRegistraPagamento}</li>
 *   <li>{@code paaSILVerificaAvviso}</li>
 *   <li>{@code paaSILRecuperaAvviso}</li>
 * </ol>
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class PagamentiTelematiciDovutiPagatiEndpoint extends AbstractSoapProxyEndpoint {

    /**
     * Namespace URI per le operazioni DovutiPagati.
     * Univoco per questo endpoint — dichiarato localmente.
     */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/ente/";

    /** Path PU per l'inoltro delle richieste alla Piattaforma Unitaria (area pagamenti MyPay). */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH_PU_MYPAY;

    /** Path di default per il fallback quando il TransportContext non è disponibile. */
    private static final String DEFAULT_PATH = Constants.DEFAULT_PATH_PA;

    public PagamentiTelematiciDovutiPagatiEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
    }

    // --- 1. paaSILImportaDovuto ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILImportaDovuto")
    @ResponsePayload
    public Element paaSILImportaDovuto(@RequestPayload Element request,
                                       MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. paaSILAutorizzaImportFlusso ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILAutorizzaImportFlusso")
    @ResponsePayload
    public Element paaSILAutorizzaImportFlusso(@RequestPayload Element request,
                                                MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 3. paaSILChiediEsitoCarrelloDovuti ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediEsitoCarrelloDovuti")
    @ResponsePayload
    public Element paaSILChiediEsitoCarrelloDovuti(@RequestPayload Element request,
                                                    MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 4. paaSILChiediPagati ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediPagati")
    @ResponsePayload
    public Element paaSILChiediPagati(@RequestPayload Element request,
                                      MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 5. paaSILChiediPagatiConRicevuta ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediPagatiConRicevuta")
    @ResponsePayload
    public Element paaSILChiediPagatiConRicevuta(@RequestPayload Element request,
                                                  MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 6. paaSILChiediPosizioniAperte ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediPosizioniAperte")
    @ResponsePayload
    public Element paaSILChiediPosizioniAperte(@RequestPayload Element request,
                                                MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 7. paaSILChiediStatoExportFlusso ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediStatoExportFlusso")
    @ResponsePayload
    public Element paaSILChiediStatoExportFlusso(@RequestPayload Element request,
                                                  MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 8. paaSILChiediStatoImportFlusso ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediStatoImportFlusso")
    @ResponsePayload
    public Element paaSILChiediStatoImportFlusso(@RequestPayload Element request,
                                                  MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 9. paaSILChiediStoricoPagamenti ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILChiediStoricoPagamenti")
    @ResponsePayload
    public Element paaSILChiediStoricoPagamenti(@RequestPayload Element request,
                                                 MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 10. paaSILInviaDovuti ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILInviaDovuti")
    @ResponsePayload
    public Element paaSILInviaDovuti(@RequestPayload Element request,
                                      MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 11. paaSILInviaCarrelloDovuti ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILInviaCarrelloDovuti")
    @ResponsePayload
    public Element paaSILInviaCarrelloDovuti(@RequestPayload Element request,
                                              MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 12. paaSILPrenotaExportFlusso ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILPrenotaExportFlusso")
    @ResponsePayload
    public Element paaSILPrenotaExportFlusso(@RequestPayload Element request,
                                              MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 13. paaSILPrenotaExportFlussoIncrementaleConRicevuta ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILPrenotaExportFlussoIncrementaleConRicevuta")
    @ResponsePayload
    public Element paaSILPrenotaExportFlussoIncrementaleConRicevuta(
            @RequestPayload Element request,
            MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 14. paaSILRegistraPagamento ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILRegistraPagamento")
    @ResponsePayload
    public Element paaSILRegistraPagamento(@RequestPayload Element request,
                                            MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 15. paaSILVerificaAvviso ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILVerificaAvviso")
    @ResponsePayload
    public Element paaSILVerificaAvviso(@RequestPayload Element request,
                                         MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 16. paaSILRecuperaAvviso ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILRecuperaAvviso")
    @ResponsePayload
    public Element paaSILRecuperaAvviso(@RequestPayload Element request,
                                         MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }
}
