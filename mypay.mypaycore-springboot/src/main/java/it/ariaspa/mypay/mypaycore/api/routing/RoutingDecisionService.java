package it.ariaspa.mypay.mypaycore.api.routing;

import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PathNonRiconosciutoException;
import it.ariaspa.mypay.mypaycore.api.config.BackendRoutingConfig;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig;
import it.ariaspa.mypay.mypaycore.api.config.PathRegistryConfig.BackendDestinatario;
import it.ariaspa.mypay.mypaycore.api.domain.EnteConfig;
import it.ariaspa.mypay.mypaycore.api.repository.EnteConfigCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Servizio centrale di decisione del routing — il "cervello" del gateway.
 *
 * <p>Data una richiesta SOAP (identificata da {@code codIpaEnte}, {@code tipoOperazione}
 * e {@code pathRichiesta}), questo servizio determina:
 * <ol>
 *   <li><strong>Destinazione</strong> (MYPAY o MYPIVOT): derivata dal path HTTP della
 *       richiesta tramite il {@link PathRegistryConfig}</li>
 *   <li><strong>Modalita di instradamento</strong> (PU con OAuth2 o legacy diretto):
 *       derivata dalla configurazione dell'ente nel database, letta tramite
 *       {@link EnteConfigCacheService}</li>
 * </ol>
 *
 * <p>Algoritmo di decisione:
 * <pre>
 * 1. Risolvere il backend di destinazione dal path (PathRegistryConfig)
 *    → se non trovato: PathNonRiconosciutoException → SOAP Fault PATH_NON_RICONOSCIUTO
 *
 * 2. Cercare la configurazione dell'ente nel DB/cache (EnteConfigCacheService)
 *    → se non trovato: EnteNonCensitoException → SOAP Fault ENTE_NON_AUTORIZZATO
 *
 * 3. Comporre la RoutingDecision con destinazione, modalita e URL backend
 * </pre>
 *
 * <p>Il servizio non esegue alcuna comunicazione HTTP — si limita a prendere la decisione.
 * L'effettivo inoltro e' responsabilita dell'endpoint SOAP che utilizza
 * {@code PiattaformaUnitariaClient} o {@code ProxyForwardingClient} in base alla decisione.
 *
 * @see RoutingDecision
 * @see PathRegistryConfig
 * @see EnteConfigCacheService
 * @see BackendRoutingConfig
 */
@Service
public class RoutingDecisionService {

    private static final Logger log = LoggerFactory.getLogger(RoutingDecisionService.class);

    private final PathRegistryConfig pathRegistryConfig;
    private final EnteConfigCacheService enteConfigCacheService;
    private final BackendRoutingConfig backendRoutingConfig;

    /**
     * Crea il servizio di decisione del routing con le dipendenze necessarie.
     *
     * @param pathRegistryConfig    registro dei path-prefix configurati
     * @param enteConfigCacheService cache delle configurazioni enti
     * @param backendRoutingConfig  configurazione degli URL dei backend
     */
    public RoutingDecisionService(PathRegistryConfig pathRegistryConfig,
                                  EnteConfigCacheService enteConfigCacheService,
                                  BackendRoutingConfig backendRoutingConfig) {
        this.pathRegistryConfig = pathRegistryConfig;
        this.enteConfigCacheService = enteConfigCacheService;
        this.backendRoutingConfig = backendRoutingConfig;
    }

    /**
     * Decide come instradare una richiesta SOAP.
     *
     * <p>Esegue il routing a due dimensioni:
     * <ol>
     *   <li>Routing per path: determina il backend di destinazione (MYPAY/MYPIVOT)</li>
     *   <li>Routing per modalita: determina se usare PU (OAuth2) o legacy (forward diretto)</li>
     * </ol>
     *
     * @param codIpaEnte     codice IPA dell'ente (estratto dall'Header SOAP)
     * @param tipoOperazione tipo di operazione SOAP (local part del messaggio)
     * @param pathRichiesta  path HTTP della richiesta (es. {@code /ws/pivot/PagamentiTelematici...})
     * @return la decisione di routing con destinazione, modalita e URL
     * @throws PathNonRiconosciutoException se il path non corrisponde a nessun backend configurato
     * @throws EnteNonCensitoException      se l'ente non ha una regola attiva per l'operazione richiesta
     */
    public RoutingDecision decide(String codIpaEnte, String tipoOperazione, String pathRichiesta) {
        log.info("Decisione di routing per: codIpaEnte='{}', tipoOperazione='{}', path='{}'",
                codIpaEnte, tipoOperazione, pathRichiesta);

        // --- Passo 1: Routing per path → destinazione backend ---
        BackendDestinatario destinazione = resolveDestinazione(pathRichiesta);

        // --- Passo 2: Routing per modalita → PU o legacy ---
        EnteConfig enteConfig = resolveEnteConfig(codIpaEnte, tipoOperazione);

        // --- Passo 3: Comporre la decisione ---
        String urlBackend = backendRoutingConfig.getBaseUrlFor(destinazione);

        RoutingDecision decision = new RoutingDecision(destinazione, enteConfig.getModalitaRouting(), urlBackend);

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
     * Recupera la configurazione di routing dell'ente dalla cache/DB.
     *
     * @param codIpaEnte     codice IPA dell'ente
     * @param tipoOperazione tipo di operazione SOAP
     * @return la configurazione dell'ente
     * @throws EnteNonCensitoException se l'ente non ha una regola attiva per l'operazione
     */
    private EnteConfig resolveEnteConfig(String codIpaEnte, String tipoOperazione) {
        Optional<EnteConfig> enteConfig = enteConfigCacheService
                .findByCodIpaEnteAndTipoOperazione(codIpaEnte, tipoOperazione);

        if (enteConfig.isEmpty()) {
            log.warn("Ente non censito: codIpaEnte='{}', tipoOperazione='{}'. "
                    + "Nessuna regola di routing attiva trovata.", codIpaEnte, tipoOperazione);
            throw new EnteNonCensitoException(codIpaEnte, tipoOperazione);
        }

        log.debug("Configurazione ente trovata: {} → modalita: {}",
                codIpaEnte, enteConfig.get().getModalitaRouting());
        return enteConfig.get();
    }
}
