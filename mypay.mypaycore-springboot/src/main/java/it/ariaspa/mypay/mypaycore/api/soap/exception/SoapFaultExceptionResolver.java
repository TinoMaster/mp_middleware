package it.ariaspa.mypay.mypaycore.api.soap.exception;

import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PathNonRiconosciutoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointExceptionResolver;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;

import java.util.Locale;

/**
 * Resolver globale per la gestione delle eccezioni negli endpoint SOAP.
 *
 * <p>Converte le eccezioni Java in SOAP Fault strutturate, garantendo che
 * i SIL ricevano sempre risposte SOAP valide anche in caso di errore.
 *
 * <p>Mapping delle eccezioni:
 * <ul>
 *   <li>{@link EnteNonCensitoException}            → SOAP Fault Client con codice ENTE_NON_AUTORIZZATO</li>
 *   <li>{@link PathNonRiconosciutoException}        → SOAP Fault Client con codice PATH_NON_RICONOSCIUTO</li>
 *   <li>{@link PiattaformaAuthenticationException}  → SOAP Fault Server con codice AUTH_ERROR</li>
 *   <li>{@link PiattaformaCommunicationException}   → SOAP Fault Server con codice COMM_ERROR</li>
 *   <li>RuntimeException generiche                  → SOAP Fault Server con codice INTERNAL_ERROR</li>
 * </ul>
 *
 * <p>Le eccezioni relative al routing (ente non censito, path non riconosciuto) generano
 * un SOAP Fault di tipo <strong>Client</strong> (errore del chiamante), mentre le eccezioni
 * di comunicazione con la PU generano un SOAP Fault di tipo <strong>Server</strong>.
 *
 * <p>Tutti gli errori vengono loggati a livello ERROR per il monitoraggio.
 */
@Component
public class SoapFaultExceptionResolver implements EndpointExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(SoapFaultExceptionResolver.class);

    /** Namespace per gli elementi di dettaglio del SOAP Fault del middleware. */
    private static final String FAULT_DETAIL_NAMESPACE =
            "http://www.regione.veneto.it/pagamenti/pivot/ente/fault";

    @Override
    public boolean resolveException(MessageContext messageContext, Object endpoint, Exception ex) {
        log.error("Errore nell'elaborazione della richiesta SOAP: [{}] {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        try {
            SoapMessage response = (SoapMessage) messageContext.getResponse();
            SoapBody soapBody = response.getSoapBody();

            SoapFault fault;

            if (ex instanceof EnteNonCensitoException enteEx) {
                // Ente non censito in mygov_ente → SOAP Fault Client (errore del chiamante)
                fault = soapBody.addClientOrSenderFault(
                        "Ente non autorizzato: codIpaEnte='" + enteEx.getCodIpaEnte()
                        + "'. L'ente non e' censito nel sistema (tabella mygov_ente).",
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, "ENTE_NON_AUTORIZZATO");

            } else if (ex instanceof PathNonRiconosciutoException pathEx) {
                // Path non riconosciuto → SOAP Fault Client (errore del chiamante)
                fault = soapBody.addClientOrSenderFault(
                        "Path non riconosciuto: '" + pathEx.getRequestPath()
                        + "'. Nessun backend di destinazione configurato per questo path.",
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, "PATH_NON_RICONOSCIUTO");

            } else if (ex instanceof PiattaformaAuthenticationException) {
                // Errore di autenticazione OAuth2 → SOAP Fault Server
                fault = soapBody.addServerOrReceiverFault(
                        "Errore di autenticazione verso la Piattaforma Unitaria: " + ex.getMessage(),
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, "AUTH_ERROR");

            } else if (ex instanceof PiattaformaCommunicationException commEx) {
                // Errore di comunicazione con PU o backend → SOAP Fault Server
                String detail = commEx.getHttpStatus() > 0
                        ? "HTTP " + commEx.getHttpStatus() + " - " + ex.getMessage()
                        : ex.getMessage();
                fault = soapBody.addServerOrReceiverFault(
                        "Errore di comunicazione con il backend: " + detail,
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, "COMM_ERROR");

            } else {
                // Errore generico → SOAP Fault Server
                fault = soapBody.addServerOrReceiverFault(
                        "Errore interno del middleware: " + ex.getMessage(),
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, "INTERNAL_ERROR");
            }

            return true;

        } catch (Exception resolverEx) {
            log.error("Errore durante la generazione del SOAP Fault: {}", resolverEx.getMessage(), resolverEx);
            return false;
        }
    }

    /**
     * Aggiunge l'elemento di dettaglio con il codice errore al SOAP Fault.
     *
     * @param fault     il SOAP Fault a cui aggiungere il dettaglio
     * @param errorCode il codice errore da inserire
     */
    private void addFaultDetailCode(SoapFault fault, String errorCode) {
        fault.addFaultDetail().addFaultDetailElement(
                new javax.xml.namespace.QName(FAULT_DETAIL_NAMESPACE, "errorCode")
        ).addText(errorCode);
    }
}
