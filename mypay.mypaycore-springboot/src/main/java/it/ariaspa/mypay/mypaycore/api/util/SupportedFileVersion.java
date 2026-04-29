package it.ariaspa.mypay.mypaycore.api.util;

/**
 * Elenca le versioni file supportate per l'import dei flussi.
 *
 * <p>Ogni costante mantiene sia la rappresentazione canonica con punto,
 * sia quella con underscore usata nel nome del file ricevuto.
 */
public enum SupportedFileVersion {

    VERSIONE_1_0("1.0", "1_0"),
    VERSIONE_1_1("1.1", "1_1"),
    VERSIONE_1_2("1.2", "1_2"),
    VERSIONE_1_3("1.3", "1_3"),
    VERSIONE_1_4("1.4", "1_4"),
    VERSIONE_1_5("1.5", "1_5");

    /** Versione nel formato canonico con separatore punto. */
    private String versione;

    /** Versione nel formato usato nel nome file con separatore underscore. */
    private String versione_file;

    /**
     * Costruisce la costante enum associando i due formati della versione.
     *
     * @param versione versione canonica nel formato {@code 1.4}
     * @param versione_file versione nel formato file {@code 1_4}
     */
    private SupportedFileVersion(String versione, String versione_file) {
        this.versione = versione;
        this.versione_file = versione_file;
    }

    private SupportedFileVersion() {
    }

    /**
     * Restituisce la versione canonica.
     *
     * @return versione nel formato con punto
     */
    public String getVersione() {
        return versione;
    }

    /**
     * Imposta la versione canonica.
     *
     * @param versione nuova versione canonica
     */
    public void setVersione(String versione) {
        this.versione = versione;
    }

    /**
     * Restituisce la versione nel formato file.
     *
     * @return versione nel formato con underscore
     */
    public String getVersione_file() {
        return versione_file;
    }

    /**
     * Imposta la versione nel formato file.
     *
     * @param versione_file nuova versione nel formato file
     */
    public void setVersione_file(String versione_file) {
        this.versione_file = versione_file;
    }

    /**
     * Cerca la versione a partire dal formato canonico con punto.
     *
     * @param value valore da confrontare
     * @return costante enum corrispondente, oppure {@code null} se non trovata
     */
    public static SupportedFileVersion GET_VERSIONE(String value) {
        for (SupportedFileVersion supportedFileVersion : SupportedFileVersion.values()) {
            if (supportedFileVersion.getVersione().equalsIgnoreCase(value)) {
                return supportedFileVersion;
            }
        }
        return null;
    }

    /**
     * Cerca la versione a partire dal formato usato nel nome file.
     *
     * @param value valore da confrontare
     * @return costante enum corrispondente, oppure {@code null} se non trovata
     */
    public static SupportedFileVersion GET_VERSIONE_FILE(String value) {
        for (SupportedFileVersion supportedFileVersion : SupportedFileVersion.values()) {
            if (supportedFileVersion.getVersione_file().equalsIgnoreCase(value)) {
                return supportedFileVersion;
            }
        }
        return null;
    }

    /**
     * Restituisce l'elenco delle versioni file supportate in formato leggibile.
     *
     * @return stringa con i valori separati da {@code |}
     */
    public static String printValues() {
        String result = "";
        for (SupportedFileVersion supportedFileVersion : SupportedFileVersion.values()) {
            result += supportedFileVersion.getVersione_file() + " | ";
        }
        return result.substring(0, result.length() - 3);
    }

}
