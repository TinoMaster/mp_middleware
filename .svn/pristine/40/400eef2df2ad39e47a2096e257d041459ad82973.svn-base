package it.ariaspa.mypay.mypaycore.api.upload;

import it.ariaspa.mypay.mypaycore.api.client.UploadForwardingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoint REST per il proxy upload dei flussi di import.
 *
 * <p>Questo controller riceve i file di upload dai SIL e li inoltra
 * al backend corretto (legacy o PU) utilizzando l'URL originale
 * salvata nella cache durante il post-processing di
 * {@code paaSILAutorizzaImportFlusso}.
 *
 * <p>Il SIL chiama questo endpoint nella stessa modalità in cui
 * chiamerebbe l'endpoint {@code MyBoxController.uploadByWS} del backend:
 * un POST multipart con {@code authorizationToken} come parametro
 * e il file nel body.
 *
 * <p>La risposta di errore replica il formato di {@code MyBoxController.uploadByWS}:
 * una lista con un oggetto contenente {@code codice} e {@code descrizione}.
 *
 * <p>Path: {@code POST /api/upload/flusso}
 *
 * @see UploadProxyCacheService
 * @see UploadForwardingClient
 */
@RestController
public class UploadFlussoController {

    private static final Logger log = LoggerFactory.getLogger(UploadFlussoController.class);

    private final UploadProxyCacheService uploadProxyCacheService;
    private final UploadForwardingClient uploadForwardingClient;

    public UploadFlussoController(UploadProxyCacheService uploadProxyCacheService,
                                  UploadForwardingClient uploadForwardingClient) {
        this.uploadProxyCacheService = uploadProxyCacheService;
        this.uploadForwardingClient = uploadForwardingClient;
    }

    /**
     * Riceve il file di upload dal SIL e lo inoltra al backend corretto.
     *
     * <p>Flusso:
     * <ol>
     *   <li>Estrae il file multipart dalla richiesta</li>
     *   <li>Recupera l'entry dalla cache usando l'authorizationToken (one-shot)</li>
     *   <li>Inoltra il file al backend (legacy o PU) tramite {@link UploadForwardingClient}</li>
     *   <li>Restituisce la risposta del backend al SIL</li>
     * </ol>
     *
     * @param authorizationToken token JWT di autorizzazione (generato dal backend durante
     *                           la chiamata a {@code paaSILAutorizzaImportFlusso})
     * @param request            la richiesta multipart contenente il file
     * @return la risposta del backend (formato JSON identico a MyBoxController.uploadByWS)
     */
    @PostMapping(path = "/api/upload/flusso", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFlusso(
            @RequestParam("authorizationToken") String authorizationToken,
            MultipartHttpServletRequest request) {

        log.info("Ricevuta richiesta di upload flusso dal SIL");

        // Estrai il file dalla richiesta multipart
        MultipartFile file = estraiFile(request);
        if (file == null || file.isEmpty()) {
            log.warn("Richiesta di upload senza file allegato o con file vuoto");
            return rispostaErrore("400", "Nessun file allegato nella richiesta di upload");
        }

        log.info("File ricevuto: nome='{}', dimensione={} byte, tipo='{}'",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        // Recupera l'entry dalla cache (one-shot: viene rimossa dopo il recupero)
        Optional<UploadProxyEntry> entryOpt = uploadProxyCacheService.recuperaERimuovi(authorizationToken);
        if (entryOpt.isEmpty()) {
            log.warn("Nessuna entry trovata nella cache per l'authorizationToken fornito. "
                    + "Potrebbe essere scaduto o già utilizzato.");
            return rispostaErrore("400", "Token di autorizzazione non valido, scaduto o già utilizzato");
        }

        UploadProxyEntry entry = entryOpt.get();
        log.info("Entry recuperata dalla cache: ente='{}', routing={}, requestToken='{}'",
                entry.getCodIpaEnte(), entry.getModalitaRouting(), entry.getRequestToken());

        // Inoltra il file al backend corretto in base alla modalità di routing dell'ente
        try {
            String rispostaBackend;
            if (entry.isPiattaformaUnitaria()) {
                rispostaBackend = uploadForwardingClient.inoltraAllaPU(
                        entry.getUploadUrlOriginale(),
                        authorizationToken,
                        file,
                        entry.getCodIpaEnte());
            } else {
                rispostaBackend = uploadForwardingClient.inoltraAlLegacy(
                        entry.getUploadUrlOriginale(),
                        authorizationToken,
                        file);
            }

            log.info("Upload completato con successo per ente '{}', requestToken '{}'",
                    entry.getCodIpaEnte(), entry.getRequestToken());

            // Restituisce la risposta del backend direttamente al SIL
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(rispostaBackend);

        } catch (Exception e) {
            log.error("Errore nell'inoltro del file per ente '{}', requestToken '{}': {}",
                    entry.getCodIpaEnte(), entry.getRequestToken(), e.getMessage(), e);
            return rispostaErrore("500", "Errore nell'inoltro del file al backend: " + e.getMessage());
        }
    }

    /**
     * Estrae il file multipart dalla richiesta HTTP.
     *
     * <p>Cerca il primo file presente nella richiesta multipart,
     * indipendentemente dal nome del parametro ({@code file} o altro).
     *
     * @param request la richiesta multipart
     * @return il file multipart, o {@code null} se non trovato
     */
    private MultipartFile estraiFile(MultipartHttpServletRequest request) {
        var fileMap = request.getFileMap();
        if (fileMap.isEmpty()) {
            return null;
        }
        // Prende il primo file presente nella richiesta
        return fileMap.values().iterator().next();
    }

    /**
     * Costruisce una risposta di errore nel formato atteso dal SIL.
     *
     * <p>Il formato replica quello di {@code MyBoxController.uploadByWS}:
     * una lista con un oggetto contenente {@code codice} e {@code descrizione}.
     *
     * @param codice      il codice di errore (es. "400", "500")
     * @param descrizione la descrizione leggibile dell'errore
     * @return la risposta HTTP con status 200 e il body di errore in formato lista
     */
    private ResponseEntity<List<Map<String, String>>> rispostaErrore(String codice, String descrizione) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("codice", codice);
        errorMap.put("descrizione", descrizione);
        // Nota: il backend MyPay restituisce HTTP 200 anche per gli errori applicativi,
        // con il codice di errore nel body. Replicare questo comportamento per compatibilità.
        return ResponseEntity.status(HttpStatus.OK).body(List.of(errorMap));
    }
}
