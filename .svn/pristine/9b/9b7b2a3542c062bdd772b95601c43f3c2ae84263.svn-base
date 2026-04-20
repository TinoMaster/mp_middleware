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
 * in questa tabella e confrontando la password fornita dal SIL nella richiesta SOAP
 * con il campo {@code dePassword}. Il campo {@code de_password} e' obbligatorio
 * in {@code mygov_ente}: ogni ente deve avere una password configurata.
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
     * Codice fiscale dell'ente pubblico (es. "80007580279").
     * Corrisponde alla colonna {@code codice_fiscale_ente} in {@code mygov_ente}.
     *
     * <p>Usato come chiave alternativa per l'identificazione dell'ente:
     * molti endpoint SOAP MyPay usano {@code <identificativoDominio>} (che contiene
     * il codice fiscale) al posto di {@code <codIpaEnte>} nell'header SOAP.
     */
    private String codiceFiscaleEnte;

    /**
     * Codice stato dell'ente nel sistema.
     * Non viene usato per la validazione del routing.
     */
    private String cdStatoEnte;

    /**
     * Password del SIL per l'autenticazione verso il middleware.
     *
     * <p>Corrisponde alla colonna {@code de_password} in {@code mygov_ente}.
     * Il campo e' obbligatorio: ogni ente deve avere una password configurata.
     *
     * <p>Non viene mai inclusa nei log ne' nella rappresentazione {@link #toString()}.
     */
    private String dePassword;

    /**
     * Costruttore vuoto per compatibilita' con i framework di mapping (Jdbi).
     */
    public Ente() {
    }

    @Override
    public String toString() {
        return "Ente{" +
                "codIpaEnte='" + codIpaEnte + '\'' +
                ", codiceFiscaleEnte='" + codiceFiscaleEnte + '\'' +
                ", deNomeEnte='" + deNomeEnte + '\'' +
                '}';
    }
}
