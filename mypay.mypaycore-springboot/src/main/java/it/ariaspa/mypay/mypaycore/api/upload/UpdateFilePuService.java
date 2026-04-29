package it.ariaspa.mypay.mypaycore.api.upload;

import it.ariaspa.mypay.mypaycore.api.util.SupportedFileVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servizio dedicato alla gestione della versione dei file di upload flusso.
 *
 * <p>Questo componente centralizza la logica di:
 * <ul>
 *   <li>estrazione della versione dal nome file originale</li>
 *   <li>validazione della versione rispetto a quelle supportate</li>
 *   <li>classificazione delle versioni per inoltro diretto o trasformazione preventiva</li>
 * </ul>
 *
 * <p>Il controller delega a questo servizio le verifiche applicative prima
 * dell'inoltro verso Piattaforma Unitaria.
 */
@Service
public class UpdateFilePuService {

    private static final Logger log = LoggerFactory.getLogger(UpdateFilePuService.class);

    /** Pattern usato per estrarre la versione finale dal nome file con estensione obbligatoria. */
    private static final Pattern PATTERN_VERSIONE_FILE = Pattern.compile("(\\d+_\\d+)(?=\\.[^.]+$)");

    /**
     * Verifica la versione del file destinato a Piattaforma Unitaria.
     *
     * <p>Le versioni {@code 1.0}, {@code 1.1}, {@code 1.2} e {@code 1.3}
     * vengono inoltrate direttamente. Le versioni {@code 1.4} e {@code 1.5}
     * vengono riconosciute ma rifiutate temporaneamente, in attesa della
     * trasformazione verso il tracciato {@code 2.0}. Qualsiasi altra versione,
     * oppure l'impossibilita' di rilevarla dal nome file, viene rifiutata con
     * errore applicativo coerente con il backend legacy.
     *
     * @param file file ricevuto dal SIL
     * @return {@code null} se la versione puo' essere inoltrata alla PU,
     *         altrimenti un {@link ErroreVersione} da restituire al chiamante
     */
    public ErroreVersione verificaVersionePerPu(MultipartFile file) {
        String nomeFile = file != null ? file.getOriginalFilename() : null;
        String versioneFile = estraiVersioneFile(nomeFile);

        if (versioneFile == null) {
            return buildErroreVersione(null);
        }

        if (SupportedFileVersion.VERSIONE_1_0.getVersione_file().equalsIgnoreCase(versioneFile) ||
                SupportedFileVersion.VERSIONE_1_1.getVersione_file().equalsIgnoreCase(versioneFile) ||
                SupportedFileVersion.VERSIONE_1_2.getVersione_file().equalsIgnoreCase(versioneFile) ||
                SupportedFileVersion.VERSIONE_1_3.getVersione_file().equalsIgnoreCase(versioneFile)) {
            return null;
        } else if (SupportedFileVersion.VERSIONE_1_4.getVersione_file().equalsIgnoreCase(versioneFile)) {
            log.info("Versione file '{}' rilevata per PU sul file '{}': non inoltrato momentaneamente da cambiare per 1_4",
                    SupportedFileVersion.VERSIONE_1_4.getVersione(), nomeFile);
            // TODO (IT): implementare la conversione del tracciato 1_4 verso 2.0.
            return buildErroreVersione(versioneFile);
        } else if (SupportedFileVersion.VERSIONE_1_5.getVersione_file().equalsIgnoreCase(versioneFile)) {
            log.info("Versione file '{}' rilevata per PU sul file '{}': non inoltrato momentaneamente da cambiare per 1_5",
                    SupportedFileVersion.VERSIONE_1_5.getVersione(), nomeFile);
            // TODO (IT): implementare la conversione del tracciato 1_5 verso 2.0.
            return buildErroreVersione(versioneFile);
        } else {
            return buildErroreVersione(versioneFile);
        }
    }

    /**
     * Estrae la versione dal nome file nel formato con underscore.
     *
     * <p>La versione viene cercata come suffisso finale del nome file,
     * immediatamente prima dell'estensione, ad esempio {@code flusso_1_4.xml}.
     *
     * @param nomeFile nome originale del file
     * @return versione nel formato {@code 1_4}, oppure {@code null} se non presente
     */
    private String estraiVersioneFile(String nomeFile) {
        if (nomeFile == null || nomeFile.isBlank()) {
            log.warn("Nome file mancante: impossibile determinare la versione del tracciato");
            return null;
        }

        Matcher matcher = PATTERN_VERSIONE_FILE.matcher(nomeFile);
        if (!matcher.find()) {
            log.warn("Impossibile estrarre la versione del tracciato dal nome file '{}'", nomeFile);
            return null;
        }

        return matcher.group(1);
    }

    /**
     * Costruisce l'errore applicativo per una versione non supportata o non rilevata.
     *
     * @param versione versione file nel formato con underscore, se disponibile
     * @return esito negativo della verifica
     */
    private ErroreVersione buildErroreVersione(String versione) {
        String valoreVersione = versione != null ? versione : "non rilevata";
        String codErrore = "PAA_IMPORT_FILE_VERSIONE_ERR(" + valoreVersione + ")";
        String deErrore = "La versione tracciato del file '" + valoreVersione
                + "' non e' supportata. Per maggiori informazioni fare riferimento al manuale 'Integrazione Ente' .";
        return new ErroreVersione(codErrore, deErrore);
    }

    /**
     * Rappresenta l'errore applicativo restituito al controller quando la
     * versione del file non e' supportata o non puo' essere determinata.
     */
    public static final class ErroreVersione {

        /** Codice errore applicativo. */
        private final String codice;

        /** Descrizione errore applicativa. */
        private final String descrizione;

        /**
         * Costruisce un nuovo errore di versione.
         *
         * @param codice codice errore applicativo
         * @param descrizione descrizione leggibile dell'errore
         */
        private ErroreVersione(String codice, String descrizione) {
            this.codice = codice;
            this.descrizione = descrizione;
        }

        /**
         * Restituisce il codice errore applicativo.
         *
         * @return codice errore
         */
        public String getCodice() {
            return codice;
        }

        /**
         * Restituisce la descrizione errore applicativa.
         *
         * @return descrizione errore
         */
        public String getDescrizione() {
            return descrizione;
        }
    }
}
