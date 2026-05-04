package it.ariaspa.mypay.mypaycore.api.soap.endpoint.mypivot;

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
 * Endpoint SOAP per la riconciliazione dei pagamenti telematici (mypivot).
 *
 * <p>Riceve le richieste SOAP dai sistemi SIL (Sistemi Informativi Locali)
 * sul path {@code /ws/pivot/PagamentiTelematiciPagatiRiconciliati} e le instrada
 * verso il backend corretto in base alla configurazione dell'ente nel database.
 *
 * <p>Namespace:
 * <ul>
 *   <li>Header: {@code http://www.regione.veneto.it/pagamenti/pivot/ente/ppthead}</li>
 *   <li>Body: {@code http://www.regione.veneto.it/pagamenti/pivot/ente/}</li>
 * </ul>
 *
 * <p>Operazioni esposte (10 in totale — contratto WSDL {@code mypivot-per-ente.wsdl}):
 * <ul>
 *   <li>{@code pivotSILAutorizzaImportFlusso} — autorizza importazione flusso generico</li>
 *   <li>{@code pivotSILAutorizzaImportFlussoRendicontazione} — autorizza importazione flusso rendicontazione</li>
 *   <li>{@code pivotSILAutorizzaImportFlussoRT} — autorizza importazione flusso RT</li>
 *   <li>{@code pivotSILAutorizzaImportFlussoTesoreria} — autorizza importazione flusso tesoreria</li>
 *   <li>{@code pivotSILChiediAccertamento} — richiede dati di accertamento</li>
 *   <li>{@code pivotSILChiediPagatiRiconciliati} — richiede pagati riconciliati</li>
 *   <li>{@code pivotSILChiediStatoExportFlussoRiconciliazione} — stato export flusso riconciliazione</li>
 *   <li>{@code pivotSILChiediStatoImportFlusso} — stato importazione flusso generico</li>
 *   <li>{@code pivotSILChiediStatoImportFlussoTesoreria} — stato importazione flusso tesoreria</li>
 *   <li>{@code pivotSILPrenotaExportFlussoRiconciliazione} — prenota export flusso riconciliazione</li>
 * </ul>
 *
 * <p>Tutte le operazioni vengono inoltrate al medesimo path sulla Piattaforma Unitaria:
 * {@code /pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati}
 *
 * @see AbstractSoapProxyEndpoint
 */
@Endpoint
public class ReconciliationEndpoint extends AbstractSoapProxyEndpoint {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEndpoint.class);

    /**
     * Namespace URI per le operazioni di riconciliazione (body).
     * Univoco per questo endpoint — dichiarato localmente.
     */
    static final String NAMESPACE_URI = "http://www.regione.veneto.it/pagamenti/pivot/ente/";

    /**
     * Percorso relativo dell'endpoint di riconciliazione sulla Piattaforma Unitaria.
     * Tutte le 10 operazioni di questo endpoint condividono lo stesso path PU (area MyPivot).
     */
    static final String PLATFORM_PATH = Constants.PLATFORM_PATH_PU_MYPIVOT;

    /**
     * Path di default per il fallback quando il TransportContext non è disponibile.
     * Centralizzato in {@link Constants#DEFAULT_PATH_PIVOT}.
     */
    private static final String DEFAULT_PATH = Constants.DEFAULT_PATH_PIVOT;

    /** Path dell'endpoint REST di upload del middleware (usato per costruire la uploadUrl sostitutiva). */
    private static final String UPLOAD_FLUSSO_PATH = "/api/upload/flusso";

    /** Servizio di cache per le entry del proxy upload flusso. */
    private final UploadProxyCacheService uploadProxyCacheService;

    /**
     * URL base del middleware esposto ai SIL (es. {@code https://middleware.example.com}).
     * Usata per costruire la {@code uploadUrl} sostitutiva nella risposta di
     * {@code pivotSILAutorizzaImportFlusso}.
     */
    private final String middlewareUploadBaseUrl;

    /**
     * Crea l'endpoint con tutte le dipendenze necessarie.
     *
     * @param piattaformaClient         client per l'inoltro verso la PU
     * @param proxyForwardingClient     client per il forward verso i backend legacy
     * @param routingDecisionService    servizio di decisione del routing
     * @param transactionLoggingService servizio per il logging transazionale
     * @param metricsService            servizio per la raccolta metriche
     * @param enteCacheService          cache degli enti con lookup duale
     * @param uploadProxyCacheService   servizio di cache per le entry del proxy upload flusso
     * @param middlewareUploadBaseUrl   URL base del middleware per il proxy upload
     */
    public ReconciliationEndpoint(PiattaformaUnitariaClient piattaformaClient,
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

    // -------------------------------------------------------------------------
    // Operazioni di autorizzazione importazione flussi
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlusso}.
     * Autorizza l'importazione di un flusso generico da parte del SIL.
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
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta (con uploadUrl sostituita se disponibile)
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlusso")
    @ResponsePayload
    public Element handleAutorizzaImportFlusso(@RequestPayload Element requestPayload,
                                               MessageContext messageContext) {
        // Step 1: Processa la richiesta normalmente (routing + inoltro al backend)
        Element responseElement = processRequest(requestPayload, messageContext, PLATFORM_PATH);

        // Step 2: Post-processing — intercetta e modifica la uploadUrl nella risposta
        try {
            postProcessAutorizzaImportFlusso(responseElement, messageContext);
        } catch (Exception e) {
            // Se il post-processing fallisce, logga ma restituisce la risposta originale.
            // Non si vuole bloccare il flusso per un errore nel post-processing.
            log.error("Errore nel post-processing di pivotSILAutorizzaImportFlusso. "
                    + "La risposta viene restituita senza modifiche. Errore: {}", e.getMessage(), e);
        }

        return responseElement;
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlussoRendicontazione}.
     * Autorizza l'importazione di un flusso di rendicontazione da parte del SIL.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoRendicontazione")
    @ResponsePayload
    public Element handleAutorizzaImportFlussoRendicontazione(@RequestPayload Element requestPayload,
                                                              MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlussoRT}.
     * Autorizza l'importazione di un flusso di Ricevute Telematiche (RT) da parte del SIL.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoRT")
    @ResponsePayload
    public Element handleAutorizzaImportFlussoRT(@RequestPayload Element requestPayload,
                                                 MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILAutorizzaImportFlussoTesoreria}.
     * Autorizza l'importazione di un flusso di tesoreria da parte del SIL.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILAutorizzaImportFlussoTesoreria")
    @ResponsePayload
    public Element handleAutorizzaImportFlussoTesoreria(@RequestPayload Element requestPayload,
                                                        MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    // -------------------------------------------------------------------------
    // Operazioni di interrogazione stato importazione flussi
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediStatoImportFlusso}.
     * Interroga lo stato di una precedente operazione di importazione flusso generico.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediStatoImportFlusso")
    @ResponsePayload
    public Element handleChiediStatoImportFlusso(@RequestPayload Element requestPayload,
                                                 MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediStatoImportFlussoTesoreria}.
     * Interroga lo stato di una precedente operazione di importazione flusso tesoreria.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediStatoImportFlussoTesoreria")
    @ResponsePayload
    public Element handleChiediStatoImportFlussoTesoreria(@RequestPayload Element requestPayload,
                                                          MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    // -------------------------------------------------------------------------
    // Operazioni di export e riconciliazione
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILPrenotaExportFlussoRiconciliazione}.
     * Prenota la generazione di un flusso di export per la riconciliazione.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILPrenotaExportFlussoRiconciliazione")
    @ResponsePayload
    public Element handlePrenotaExportFlussoRiconciliazione(@RequestPayload Element requestPayload,
                                                            MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediStatoExportFlussoRiconciliazione}.
     * Interroga lo stato di una precedente prenotazione di export flusso riconciliazione.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediStatoExportFlussoRiconciliazione")
    @ResponsePayload
    public Element handleChiediStatoExportFlussoRiconciliazione(@RequestPayload Element requestPayload,
                                                                MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    // -------------------------------------------------------------------------
    // Operazioni di interrogazione pagati e accertamento
    // -------------------------------------------------------------------------

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediPagatiRiconciliati}.
     * Recupera i pagamenti riconciliati per un ente, filtrabili per IUV/IUF o per data.
     *
     * <p>Nota: questa operazione non richiede header {@code intestazionePPT} nel contratto
     * WSDL originale; l'identificazione dell'ente avviene tramite {@code codIpaEnte}
     * presente nel body della richiesta.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediPagatiRiconciliati")
    @ResponsePayload
    public Element handleChiediPagatiRiconciliati(@RequestPayload Element requestPayload,
                                                  MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    /**
     * Gestisce la richiesta SOAP {@code pivotSILChiediAccertamento}.
     * Recupera i dati di accertamento per un ente, ricercabili per bolletta o per IUF.
     *
     * @param requestPayload l'elemento XML del body
     * @param messageContext il contesto del messaggio SOAP
     * @return l'elemento XML della risposta dal backend selezionato
     */
    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "pivotSILChiediAccertamento")
    @ResponsePayload
    public Element handleChiediAccertamento(@RequestPayload Element requestPayload,
                                            MessageContext messageContext) {
        return processRequest(requestPayload, messageContext, PLATFORM_PATH);
    }

    // =====================================================================
    // Metodi privati per il post-processing della risposta di autorizzazione import flusso
    // =====================================================================

    /**
     * Post-processa la risposta di pivotSILAutorizzaImportFlusso.
     *
     * <p>Estrae i 4 campi dalla risposta (uploadUrl, authorizationToken, requestToken, importPath),
     * salva l'URL originale nella cache upload proxy, e sostituisce la uploadUrl con quella
     * del middleware.
     *
     * <p>Quando il routing è verso PIATTAFORMA_UNITARIA, il campo {@code authorizationToken}
     * nella risposta viene sostituito con {@code requestToken} perché la PU non restituisce
     * un token univoco in quel campo. La cache viene indicizzata con il valore inviato al SIL
     * ({@code requestToken} per PU, {@code authorizationToken} originale per legacy).
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
            log.debug("Risposta di pivotSILAutorizzaImportFlusso senza uploadUrl o authorizationToken. "
                    + "Probabile fault dal backend — nessun post-processing necessario.");
            return;
        }

        // Estrai il codIpaEnte dal contesto SOAP per determinare la modalità di routing
        String codIpaEnte = estraiCodIpaEnteDaContesto(messageContext);
        ModalitaRouting modalitaRouting = determinaModalitaRouting(codIpaEnte);

        // Determina la chiave di cache e il token da restituire al SIL nella risposta SOAP.
        // Per PIATTAFORMA_UNITARIA: la PU non restituisce un authorizationToken univoco,
        // quindi usiamo requestToken (univoco) come chiave di cache e lo sostituiamo nella risposta.
        // Per LEGACY: il backend restituisce un authorizationToken univoco, usato come chiave.
        String cacheKey;
        String authorizationTokenSil;
        if (modalitaRouting == ModalitaRouting.PIATTAFORMA_UNITARIA) {
            cacheKey = requestToken;
            authorizationTokenSil = requestToken;
            sostituisciTestoTag(responseElement, "authorizationToken", authorizationTokenSil);
            log.debug("Routing PU: authorizationToken originale '{}' sostituito con requestToken '{}'",
                    authorizationToken, requestToken);
        } else {
            cacheKey = authorizationToken;
            authorizationTokenSil = authorizationToken;
        }

        // Salva nella cache l'entry associata, marcando l'origine come MYPIVOT
        // per saltare la verifica versione in UploadFlussoController
        UploadProxyEntry entry = new UploadProxyEntry(
                uploadUrl, authorizationTokenSil, requestToken, importPath,
                modalitaRouting, codIpaEnte, BackendDestinatario.MYPIVOT);
        uploadProxyCacheService.salva(cacheKey, entry);

        // Sostituisce la uploadUrl nella risposta con l'URL del middleware
        String middlewareUploadUrl = middlewareUploadBaseUrl + UPLOAD_FLUSSO_PATH;
        sostituisciTestoTag(responseElement, "uploadUrl", middlewareUploadUrl);

        log.info("Post-processing pivotSILAutorizzaImportFlusso completato per ente '{}' (routing: {}). "
                + "uploadUrl sostituita: '{}' → '{}', cacheKey='{}'",
                codIpaEnte, modalitaRouting, uploadUrl, middlewareUploadUrl, cacheKey);
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

    @Override
    protected String getDefaultPath() {
        return DEFAULT_PATH;
    }

    /**
     * Namespace del fault detail per l'endpoint MyPivot (area pivot/riconciliazione).
     */
    @Override
    public String getFaultDetailNamespace() {
        return Constants.NS_FAULT_MYPIVOT;
    }
}
