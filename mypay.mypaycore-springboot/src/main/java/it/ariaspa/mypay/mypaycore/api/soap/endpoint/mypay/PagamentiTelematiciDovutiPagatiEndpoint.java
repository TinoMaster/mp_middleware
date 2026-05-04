package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypay;

import it.ariaspa.mypay.mypaycore.api.client.PiattaformaUnitariaClient;
import it.ariaspa.mypay.mypaycore.api.client.ProxyForwardingClient;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.logging.TransactionLoggingService;
import it.ariaspa.mypay.mypaycore.api.metrics.MiddlewareMetricsService;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import it.ariaspa.mypay.mypaycore.api.routing.RoutingDecisionService;
import it.ariaspa.mypay.mypaycore.api.soap.endpoint.AbstractSoapProxyEndpoint;
import it.ariaspa.mypay.mypaycore.api.upload.UploadProxyCacheService;
import it.ariaspa.mypay.mypaycore.api.upload.UploadProxyEntry;
import it.ariaspa.mypay.mypaycore.api.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
 *   <li>{@code paaSILAutorizzaImportFlusso} — con post-processing per proxy upload</li>
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
 * @see it.ariaspa.mypay.mypaycore.api.upload.UploadFlussoController
 */
@Endpoint
public class PagamentiTelematiciDovutiPagatiEndpoint extends AbstractSoapProxyEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PagamentiTelematiciDovutiPagatiEndpoint.class);

    /**
     * Namespace URI per le operazioni DovutiPagati.
     * Univoco per questo endpoint — dichiarato localmente.
     */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/ente/";

    /** Path PU per l'inoltro delle richieste alla Piattaforma Unitaria (area pagamenti MyPay). */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH_PU_MYPAY;

    /** Path di default per il fallback quando il TransportContext non è disponibile. */
    private static final String DEFAULT_PATH = Constants.DEFAULT_PATH_PA;

    /** Path dell'endpoint REST di upload del middleware (usato per costruire la uploadUrl sostitutiva). */
    public static final String UPLOAD_FLUSSO_PATH = "/api/upload/flusso";

    /** Servizio di cache per le entry del proxy upload flusso. */
    private final UploadProxyCacheService uploadProxyCacheService;

    /**
     * URL base del middleware esposto ai SIL (es. {@code https://middleware.example.com}).
     * Usata per costruire la {@code uploadUrl} sostitutiva nella risposta di
     * {@code paaSILAutorizzaImportFlusso}.
     */
    private final String middlewareUploadBaseUrl;

    public PagamentiTelematiciDovutiPagatiEndpoint(
            PiattaformaUnitariaClient piattaformaClient,
            ProxyForwardingClient proxyForwardingClient,
            RoutingDecisionService routingDecisionService,
            TransactionLoggingService transactionLoggingService,
            MiddlewareMetricsService metricsService,
            EnteCacheService enteCacheService,
            UploadProxyCacheService uploadProxyCacheService,
            @Value("${middleware.upload.proxy.base-url}") String middlewareUploadBaseUrl) {
        super(piattaformaClient, proxyForwardingClient, routingDecisionService,
                transactionLoggingService, metricsService, enteCacheService);
        this.uploadProxyCacheService = uploadProxyCacheService;
        this.middlewareUploadBaseUrl = middlewareUploadBaseUrl;
    }

    // --- 1. paaSILImportaDovuto ---
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILImportaDovuto")
    @ResponsePayload
    public Element paaSILImportaDovuto(@RequestPayload Element request,
                                       MessageContext messageContext) {
        return processRequest(request, messageContext, PLATFORM_PATH);
    }

    // --- 2. paaSILAutorizzaImportFlusso --- (con post-processing: proxy upload)
    /**
     * Operazione paaSILAutorizzaImportFlusso con post-processing della risposta per il proxy upload.
     *
     * <p>Dopo aver ricevuto la risposta dal backend tramite {@link #processRequest}, questo metodo:
     * <ol>
     *   <li>Estrae i campi {@code uploadUrl}, {@code authorizationToken},
     *       {@code requestToken} e {@code importPath} dalla risposta</li>
     *   <li>Salva l'URL originale nella cache del proxy upload associata all'authorizationToken</li>
     *   <li>Sostituisce {@code uploadUrl} nella risposta con l'URL dell'endpoint REST del middleware</li>
     * </ol>
     *
     * <p>Se il post-processing fallisce (es. risposta con fault), la risposta originale viene
     * restituita invariata senza bloccare il flusso.
     *
     * @param request        payload XML della richiesta SOAP
     * @param messageContext contesto del messaggio SOAP (usato per estrarre il codIpaEnte)
     * @return elemento XML della risposta (con uploadUrl sostituita se disponibile)
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "paaSILAutorizzaImportFlusso")
    @ResponsePayload
    public Element paaSILAutorizzaImportFlusso(@RequestPayload Element request,
                                                MessageContext messageContext) {
        // Step 1: Processa la richiesta normalmente (routing + inoltro al backend)
        Element responseElement = processRequest(request, messageContext, PLATFORM_PATH);

        // Step 2: Post-processing — intercetta e modifica la uploadUrl nella risposta
        try {
            postProcessAutorizzaImportFlusso(responseElement, messageContext);
        } catch (Exception e) {
            // Se il post-processing fallisce, logga ma restituisce la risposta originale.
            // Non si vuole bloccare il flusso per un errore nel post-processing.
            log.error("Errore nel post-processing di paaSILAutorizzaImportFlusso. "
                    + "La risposta viene restituita senza modifiche. Errore: {}", e.getMessage(), e);
        }

        return responseElement;
    }

    /**
     * Post-processa la risposta di paaSILAutorizzaImportFlusso.
     *
     * <p>Estrae i 4 campi dalla risposta (uploadUrl, authorizationToken, requestToken, importPath),
     * salva l'URL originale nella cache upload proxy, e sostituisce la uploadUrl con quella
     * del middleware.
     *
     * @param responseElement la risposta XML dal backend (body del SOAP Envelope)
     * @param messageContext  il contesto SOAP (per estrarre il codIpaEnte)
     */
    private void postProcessAutorizzaImportFlusso(Element responseElement,
                                                   MessageContext messageContext) {
        // Estrai i campi dalla risposta
        String uploadUrl = estraiTestoTag(responseElement, "uploadUrl");
        String authorizationToken = estraiTestoTag(responseElement, "authorizationToken");
        String requestToken = estraiTestoTag(responseElement, "requestToken");
        String importPath = estraiTestoTag(responseElement, "importPath");

        // Se manca uploadUrl o authorizationToken, la risposta contiene probabilmente un fault:
        // non processare (non c'è nulla da sostituire)
        if (uploadUrl == null || authorizationToken == null) {
            log.debug("Risposta di paaSILAutorizzaImportFlusso senza uploadUrl o authorizationToken. "
                    + "Probabile fault dal backend — nessun post-processing necessario.");
            return;
        }

        // Estrai il codIpaEnte dal contesto SOAP per determinare la modalità di routing
        String codIpaEnte = estraiCodIpaEnteDaContesto(messageContext);
        ModalitaRouting modalitaRouting = determinaModalitaRouting(codIpaEnte);

        // Salva nella cache l'entry associata all'authorizationToken
        UploadProxyEntry entry = new UploadProxyEntry(
                uploadUrl, authorizationToken, requestToken, importPath,
                modalitaRouting, codIpaEnte, BackendDestinatario.MYPAY);
        uploadProxyCacheService.salva(authorizationToken, entry);

        // Sostituisce la uploadUrl nella risposta con l'URL del middleware
        String middlewareUploadUrl = middlewareUploadBaseUrl + UPLOAD_FLUSSO_PATH;
        sostituisciTestoTag(responseElement, "uploadUrl", middlewareUploadUrl);

        log.info("Post-processing paaSILAutorizzaImportFlusso completato per ente '{}' (routing: {}). "
                + "uploadUrl sostituita: '{}' → '{}'",
                codIpaEnte, modalitaRouting, uploadUrl, middlewareUploadUrl);
    }

    /**
     * Estrae il codice IPA dell'ente dal contesto del messaggio SOAP.
     *
     * <p>Richiama {@link #extractFullSoapEnvelope} e {@link #extractEnteIdentifier}
     * per ricavare il codIpaEnte dal SOAP Envelope corrente.
     *
     * @param messageContext il contesto del messaggio SOAP
     * @return il codice IPA dell'ente, o {@code "SCONOSCIUTO"} se non estraibile
     */
    private String estraiCodIpaEnteDaContesto(MessageContext messageContext) {
        try {
            String soapEnvelope = extractFullSoapEnvelope(messageContext);
            return extractEnteIdentifier(soapEnvelope);
        } catch (Exception e) {
            log.warn("Impossibile estrarre codIpaEnte dal contesto SOAP per il post-processing: {}",
                    e.getMessage());
            return "SCONOSCIUTO";
        }
    }

    /**
     * Determina la modalità di routing per un ente consultando la cache degli enti.
     *
     * @param codIpaEnte il codice IPA dell'ente
     * @return {@link ModalitaRouting#PIATTAFORMA_UNITARIA} se l'ente è configurato per PU,
     *         {@link ModalitaRouting#LEGACY} altrimenti (default sicuro)
     */
    private ModalitaRouting determinaModalitaRouting(String codIpaEnte) {
        try {
            return enteCacheService.findByCodIpaEnte(codIpaEnte)
                    .map(ente -> ente.isPiattaformaUnitaria()
                            ? ModalitaRouting.PIATTAFORMA_UNITARIA
                            : ModalitaRouting.LEGACY)
                    .orElse(ModalitaRouting.LEGACY);
        } catch (Exception e) {
            log.warn("Impossibile determinare la modalità di routing per ente '{}': {}. "
                    + "Default a LEGACY.", codIpaEnte, e.getMessage());
            return ModalitaRouting.LEGACY;
        }
    }

    /**
     * Estrae il contenuto testuale di un tag XML dall'elemento (ricerca per nome locale).
     *
     * @param element l'elemento XML in cui cercare
     * @param tagName il nome locale del tag
     * @return il contenuto testuale trimmed, o {@code null} se non trovato o vuoto
     */
    private String estraiTestoTag(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String value = nodes.item(0).getTextContent().trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    /**
     * Sostituisce il contenuto testuale di un tag XML nell'elemento.
     *
     * @param element     l'elemento XML in cui cercare
     * @param tagName     il nome locale del tag
     * @param nuovoValore il nuovo valore testuale da impostare
     */
    private void sostituisciTestoTag(Element element, String tagName, String nuovoValore) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            nodes.item(0).setTextContent(nuovoValore);
        }
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

    /**
     * Namespace del fault detail per gli endpoint MyPay (area ente/pagamenti).
     */
    @Override
    public String getFaultDetailNamespace() {
        return Constants.NS_FAULT_MYPAY;
    }
}
