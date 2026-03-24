package it.ariaspa.mypay.mypaycore.api.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Modello di dominio che rappresenta un ente pubblico registrato nel sistema.
 *
 * <p>Mappa la tabella condivisa {@code mygov_ente}, che e' la sorgente primaria
 * di tutti gli enti abilitati a interagire con il middleware. Questa tabella e'
 * condivisa con le applicazioni mypay e mypivot.
 *
 * <p>La validazione dell'ente avviene verificando la presenza del {@code codIpaEnte}
 * in questa tabella. Non vengono verificati ne' la password ne' lo stato dell'ente.
 */
@Getter
@Setter
public class Ente {

    /** Identificativo univoco del record (chiave surrogata). */
    private Long mygovEnteId;

    /**
     * Codice IPA dell'ente pubblico — identificativo univoco nel sistema.
     * Esempio: {@code "R_LOMBARDIA"}, {@code "C_F205"}.
     */
    private String codIpaEnte;

    /** Denominazione dell'ente (es. "Regione Lombardia"). */
    private String deNomeEnte;

    /**
     * Codice stato dell'ente nel sistema.
     * Non viene usato per la validazione del routing.
     */
    private String cdStatoEnte;

    /**
     * Costruttore vuoto per compatibilita' con i framework di mapping (Jdbi).
     */
    public Ente() {
    }

    @Override
    public String toString() {
        return "Ente{" +
                "codIpaEnte='" + codIpaEnte + '\'' +
                ", deNomeEnte='" + deNomeEnte + '\'' +
                '}';
    }
}
