package it.ariaspa.mypay.mypaycore.api.routing;

import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PathNonRiconosciutoException;
import it.ariaspa.mypay.mypaycore.api.config.BackendRoutingConfig;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.EnteCompleto;
import it.ariaspa.mypay.mypaycore.api.domain.ModalitaRouting;
import it.ariaspa.mypay.mypaycore.api.repository.EnteCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Servizio centrale di decisione del routing — il "cervello" del gateway.
 *
 * <p>Data una richiesta SOAP (identificata da {@code codIpaEnte} e {@code pathRichiesta}),
 * questo servizio determina:
 * <ol>
 *   <li><strong>Destinazione</strong> (MYPAY o MYPIVOT): derivata dal path HTTP della
 *       richiesta tramite il {@link PathRegistryConfig}</li>
 *   <li><strong>Modalita' di instradamento</strong> (PU con OAuth2 o legacy diretto):
 *       derivata dalla presenza di una configurazione PU attiva per l'ente in
 *       {@link EnteCacheService}</li>
 * </ol>
 *
 * <p>Algoritmo di decisione:
 * <pre>
 * 1. Risolvere il backend di destinazione dal path (PathRegistryConfig)
 *    → se non trovato: PathNonRiconosciutoException → SOAP Fault PATH_NON_RICONOSCIUTO
 *
 * 2. Cercare l'ente nel DB/cache (EnteCacheService)
 *    → se non trovato: EnteNonCensitoException → SOAP Fault ENTE_NON_AUTORIZZATO
 *
 * 3. Determinare la modalita' dal flag isPiattaformaUnitaria() dell'EnteCompleto
 *
 * 4. Comporre la RoutingDecision con destinazione, modalita', URL backend ed EnteCompleto
 * </pre>
 *
 * <p>Il servizio non esegue alcuna comunicazione HTTP — si limita a prendere la decisione.
 * L'effettivo inoltro e' responsabilita' dell'endpoint SOAP che utilizza
 * {@code PiattaformaUnitariaClient} o {@code ProxyForwardingClient} in base alla decisione.
 *
 * @see RoutingDecision
 * @see PathRegistryConfig
 * @see EnteCacheService
 * @see BackendRoutingConfig
 */
@Service
public class RoutingDecisionService {

    private static final Logger log = LoggerFactory.getLogger(RoutingDecisionService.class);

    private final PathRegistryConfig pathRegistryConfig;
    private final EnteCacheService enteCacheService;
    private final BackendRoutingConfig backendRoutingConfig;

    /**
     * Crea il servizio di decisione del routing con le dipendenze necessarie.
     *
     * @param pathRegistryConfig   registro dei path-prefix configurati
     * @param enteCacheService     cache degli enti con le loro configurazioni PU
     * @param backendRoutingConfig configurazione degli URL dei backend
     */
    public RoutingDecisionService(PathRegistryConfig pathRegistryConfig,
                                  EnteCacheService enteCacheService,
                                  BackendRoutingConfig backendRoutingConfig) {
        this.pathRegistryConfig = pathRegistryConfig;
        this.enteCacheService = enteCacheService;
        this.backendRoutingConfig = backendRoutingConfig;
    }

    /**
     * Decide come instradare una richiesta SOAP.
     *
     * <p>Esegue il routing a due dimensioni:
     * <ol>
     *   <li>Routing per path: determina il backend di destinazione (MYPAY/MYPIVOT)</li>
     *   <li>Routing per modalita': determina se usare PU (OAuth2) o legacy (forward diretto)
     *       in base alla configurazione dell'ente nel database</li>
     * </ol>
     *
     * @param codIpaEnte    codice IPA dell'ente (estratto dall'Header SOAP)
     * @param pathRichiesta path HTTP della richiesta (es. {@code /ws/pivot/PagamentiTelematici...})
     * @return la decisione di routing con destinazione, modalita', URL e dati dell'ente
     * @throws PathNonRiconosciutoException se il path non corrisponde a nessun backend configurato
     * @throws EnteNonCensitoException      se l'ente non e' censito in {@code mygov_ente}
     */
    public RoutingDecision decide(String codIpaEnte, String pathRichiesta) {
        log.info("Decisione di routing per: codIpaEnte='{}', path='{}'",
                codIpaEnte, pathRichiesta);

        // --- Passo 1: Routing per path → destinazione backend ---
        BackendDestinatario destinazione = resolveDestinazione(pathRichiesta);

        // --- Passo 2: Lookup ente → PU o legacy ---
        EnteCompleto ente = resolveEnte(codIpaEnte);

        // --- Passo 3: Determinare la modalita' di routing ---
        ModalitaRouting modalita = ente.isPiattaformaUnitaria()
                ? ModalitaRouting.PIATTAFORMA_UNITARIA
                : ModalitaRouting.LEGACY;

        // --- Passo 4: Comporre la decisione ---
        String urlBackend = backendRoutingConfig.getBaseUrlFor(destinazione);
        RoutingDecision decision = new RoutingDecision(destinazione, modalita, urlBackend, ente);

        log.info("Decisione di routing completata: {}", decision);
        return decision;
    }

    /**
     * Risolve il backend di destinazione dal path della richiesta.
     *
     * @param pathRichiesta path HTTP della richiesta
     * @return il backend di destinazione
     * @throws PathNonRiconosciutoException se nessun backend corrisponde al path
     */
    private BackendDestinatario resolveDestinazione(String pathRichiesta) {
        Optional<BackendDestinatario> destinazione = pathRegistryConfig.resolveBackend(pathRichiesta);

        if (destinazione.isEmpty()) {
            log.warn("Path non riconosciuto: '{}'. Nessun backend configurato.", pathRichiesta);
            throw new PathNonRiconosciutoException(pathRichiesta);
        }

        log.debug("Path '{}' risolto al backend: {}", pathRichiesta, destinazione.get());
        return destinazione.get();
    }

    /**
     * Recupera i dati dell'ente dalla cache/DB.
     *
     * @param codIpaEnte codice IPA dell'ente
     * @return le informazioni complete dell'ente con configurazione PU opzionale
     * @throws EnteNonCensitoException se l'ente non e' censito in {@code mygov_ente}
     */
    private EnteCompleto resolveEnte(String codIpaEnte) {
        Optional<EnteCompleto> ente = enteCacheService.findByCodIpaEnte(codIpaEnte);

        if (ente.isEmpty()) {
            log.warn("Ente non censito: codIpaEnte='{}'. Non trovato in mygov_ente.", codIpaEnte);
            throw new EnteNonCensitoException(codIpaEnte);
        }

        log.debug("Ente trovato: {} → piattaformaUnitaria: {}",
                codIpaEnte, ente.get().isPiattaformaUnitaria());
        return ente.get();
    }
}
