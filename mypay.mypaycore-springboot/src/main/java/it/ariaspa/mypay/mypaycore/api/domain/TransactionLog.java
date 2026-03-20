package it.ariaspa.mypay.mypaycore.api.domain;

import java.time.LocalDateTime;

/**
 * Modello di dominio che rappresenta il log di una singola transazione SOAP
 * processata dal middleware.
 *
 * <p>Ogni richiesta SOAP ricevuta dal middleware genera un record in
 * {@code mwpay_transaction_log} con le informazioni di routing, esito e durata.
 * Lo scopo e' garantire tracciabilita completa per auditing e diagnostica.
 *
 * <p>Il logging e' sincrono (post-request) ma non bloccante: se l'inserimento
 * in DB fallisce, viene registrato un warning nel log applicativo senza
 * interrompere la risposta al SIL.
 *
 * @see EnteConfig
 * @see ModalitaRouting
 */
public class TransactionLog {

    /** Identificativo univoco del log (chiave surrogata auto-generata). */
    private Long id;

    /** Codice IPA dell'ente che ha effettuato la richiesta. */
    private String codIpaEnte;

    /** Tipo di operazione SOAP (local part del messaggio). */
    private String tipoOperazione;

    /** Modalita di instradamento utilizzata (PU o legacy). */
    private ModalitaRouting modalitaRouting;

    /**
     * Backend di destinazione determinato dal path della richiesta.
     * Valori: {@code "MYPAY"} o {@code "MYPIVOT"}.
     */
    private String destinazione;

    /** Path HTTP della richiesta SOAP ricevuta dal SIL. */
    private String pathRichiesta;

    /** Codice di stato HTTP della risposta ricevuta dal backend (null se non disponibile). */
    private Integer httpStatusRisposta;

    /**
     * Esito della transazione.
     * Valori: {@code "OK"} o {@code "ERRORE"}.
     */
    private String esito;

    /** Messaggio di errore (solo se esito = ERRORE, senza dati sensibili). */
    private String messaggioErrore;

    /** Durata della transazione in millisecondi (tempo totale di processing). */
    private Long durataMs;

    /** Timestamp della richiesta SOAP ricevuta dal middleware. */
    private LocalDateTime timestampRichiesta;

    /**
     * Costruttore vuoto per compatibilita con i framework di mapping (Jdbi, Jackson).
     */
    public TransactionLog() {
    }

    /**
     * Costruttore completo per la creazione di un'istanza con tutti i campi.
     *
     * @param id                  identificativo univoco
     * @param codIpaEnte          codice IPA dell'ente
     * @param tipoOperazione      tipo di operazione SOAP
     * @param modalitaRouting     modalita di instradamento
     * @param destinazione        backend di destinazione
     * @param pathRichiesta       path HTTP della richiesta
     * @param httpStatusRisposta  codice HTTP della risposta dal backend
     * @param esito               esito della transazione
     * @param messaggioErrore     messaggio di errore (opzionale)
     * @param durataMs            durata in millisecondi
     * @param timestampRichiesta  timestamp della richiesta
     */
    public TransactionLog(Long id, String codIpaEnte, String tipoOperazione,
                          ModalitaRouting modalitaRouting, String destinazione,
                          String pathRichiesta, Integer httpStatusRisposta,
                          String esito, String messaggioErrore, Long durataMs,
                          LocalDateTime timestampRichiesta) {
        this.id = id;
        this.codIpaEnte = codIpaEnte;
        this.tipoOperazione = tipoOperazione;
        this.modalitaRouting = modalitaRouting;
        this.destinazione = destinazione;
        this.pathRichiesta = pathRichiesta;
        this.httpStatusRisposta = httpStatusRisposta;
        this.esito = esito;
        this.messaggioErrore = messaggioErrore;
        this.durataMs = durataMs;
        this.timestampRichiesta = timestampRichiesta;
    }

    // --- Getter e Setter ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodIpaEnte() {
        return codIpaEnte;
    }

    public void setCodIpaEnte(String codIpaEnte) {
        this.codIpaEnte = codIpaEnte;
    }

    public String getTipoOperazione() {
        return tipoOperazione;
    }

    public void setTipoOperazione(String tipoOperazione) {
        this.tipoOperazione = tipoOperazione;
    }

    public ModalitaRouting getModalitaRouting() {
        return modalitaRouting;
    }

    public void setModalitaRouting(ModalitaRouting modalitaRouting) {
        this.modalitaRouting = modalitaRouting;
    }

    public String getDestinazione() {
        return destinazione;
    }

    public void setDestinazione(String destinazione) {
        this.destinazione = destinazione;
    }

    public String getPathRichiesta() {
        return pathRichiesta;
    }

    public void setPathRichiesta(String pathRichiesta) {
        this.pathRichiesta = pathRichiesta;
    }

    public Integer getHttpStatusRisposta() {
        return httpStatusRisposta;
    }

    public void setHttpStatusRisposta(Integer httpStatusRisposta) {
        this.httpStatusRisposta = httpStatusRisposta;
    }

    public String getEsito() {
        return esito;
    }

    public void setEsito(String esito) {
        this.esito = esito;
    }

    public String getMessaggioErrore() {
        return messaggioErrore;
    }

    public void setMessaggioErrore(String messaggioErrore) {
        this.messaggioErrore = messaggioErrore;
    }

    public Long getDurataMs() {
        return durataMs;
    }

    public void setDurataMs(Long durataMs) {
        this.durataMs = durataMs;
    }

    public LocalDateTime getTimestampRichiesta() {
        return timestampRichiesta;
    }

    public void setTimestampRichiesta(LocalDateTime timestampRichiesta) {
        this.timestampRichiesta = timestampRichiesta;
    }

    @Override
    public String toString() {
        return "TransactionLog{" +
                "id=" + id +
                ", codIpaEnte='" + codIpaEnte + '\'' +
                ", tipoOperazione='" + tipoOperazione + '\'' +
                ", modalitaRouting=" + modalitaRouting +
                ", destinazione='" + destinazione + '\'' +
                ", esito='" + esito + '\'' +
                ", durataMs=" + durataMs +
                '}';
    }
}
