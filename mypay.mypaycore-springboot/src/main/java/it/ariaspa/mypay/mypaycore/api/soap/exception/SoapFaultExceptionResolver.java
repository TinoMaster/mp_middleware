package it.ariaspa.mypay.mypaycore.api.soap.exception;

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
 * Converte le eccezioni Java in SOAP Fault strutturate, garantendo che
 * i SIL ricevano sempre risposte SOAP valide anche in caso di errore.
 *
 * Mapping delle eccezioni:
 * - PiattaformaAuthenticationException -> SOAP Fault Server con codice AUTH_ERROR
 * - PiattaformaCommunicationException  -> SOAP Fault Server con codice COMM_ERROR
 * - RuntimeException generiche         -> SOAP Fault Server con codice INTERNAL_ERROR
 *
 * Tutti gli errori vengono loggati a livello ERROR per il monitoraggio.
 */
@Component
public class SoapFaultExceptionResolver implements EndpointExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(SoapFaultExceptionResolver.class);

    @Override
    public boolean resolveException(MessageContext messageContext, Object endpoint, Exception ex) {
        log.error("Errore nell'elaborazione della richiesta SOAP: [{}] {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        try {
            SoapMessage response = (SoapMessage) messageContext.getResponse();
            SoapBody soapBody = response.getSoapBody();

            SoapFault fault;

            if (ex instanceof PiattaformaAuthenticationException) {
                fault = soapBody.addServerOrReceiverFault(
                        "Errore di autenticazione verso la Piattaforma Unitaria: " + ex.getMessage(),
                        Locale.ITALIAN
                );
                fault.addFaultDetail().addFaultDetailElement(
                        new javax.xml.namespace.QName("http://www.regione.lombardia.it/mypay/fault", "errorCode")
                ).addText("AUTH_ERROR");

            } else if (ex instanceof PiattaformaCommunicationException commEx) {
                String detail = commEx.getHttpStatus() > 0
                        ? "HTTP " + commEx.getHttpStatus() + " - " + ex.getMessage()
                        : ex.getMessage();
                fault = soapBody.addServerOrReceiverFault(
                        "Errore di comunicazione con la Piattaforma Unitaria: " + detail,
                        Locale.ITALIAN
                );
                fault.addFaultDetail().addFaultDetailElement(
                        new javax.xml.namespace.QName("http://www.regione.lombardia.it/mypay/fault", "errorCode")
                ).addText("COMM_ERROR");

            } else {
                fault = soapBody.addServerOrReceiverFault(
                        "Errore interno del middleware: " + ex.getMessage(),
                        Locale.ITALIAN
                );
                fault.addFaultDetail().addFaultDetailElement(
                        new javax.xml.namespace.QName("http://www.regione.lombardia.it/mypay/fault", "errorCode")
                ).addText("INTERNAL_ERROR");
            }

            return true;

        } catch (Exception resolverEx) {
            log.error("Errore durante la generazione del SOAP Fault: {}", resolverEx.getMessage(), resolverEx);
            return false;
        }
    }
}
