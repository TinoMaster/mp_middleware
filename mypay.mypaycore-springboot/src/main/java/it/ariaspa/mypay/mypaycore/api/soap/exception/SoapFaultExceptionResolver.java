package it.ariaspa.mypay.mypaycore.api.soap.exception;

import it.ariaspa.mypay.mypaycore.api.common.exception.CredenzialeSilNonValidaException;
import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonIdentificabileException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PathNonRiconosciutoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import it.ariaspa.mypay.mypaycore.api.soap.endpoint.AbstractSoapProxyEndpoint;
import it.ariaspa.mypay.mypaycore.api.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointExceptionResolver;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;

import javax.xml.namespace.QName;
import java.util.Locale;

/**
 * Resolver globale per la gestione delle eccezioni negli endpoint SOAP.
 *
 * <p>Converte le eccezioni Java in SOAP Fault strutturate, garantendo che
 * i SIL ricevano sempre risposte SOAP valide anche in caso di errore.
 *
 * <p>Mapping delle eccezioni:
 * <ul>
 *   <li>{@link EnteNonCensitoException}             → SOAP Fault Client con codice {@link FaultCode#ENTE_NON_AUTORIZZATO}</li>
 *   <li>{@link EnteNonIdentificabileException}       → SOAP Fault Client con codice {@link FaultCode#ENTE_NON_IDENTIFICABILE}</li>
 *   <li>{@link CredenzialeSilNonValidaException}     → SOAP Fault Client con codice {@link FaultCode#CREDENZIALI_NON_VALIDE}</li>
 *   <li>{@link PathNonRiconosciutoException}         → SOAP Fault Client con codice {@link FaultCode#PATH_NON_RICONOSCIUTO}</li>
 *   <li>{@link PiattaformaAuthenticationException}   → SOAP Fault Server con codice {@link FaultCode#AUTH_ERROR}</li>
 *   <li>{@link PiattaformaCommunicationException}    → SOAP Fault Server con codice {@link FaultCode#COMM_ERROR}</li>
 *   <li>RuntimeException generiche                   → SOAP Fault Server con codice {@link FaultCode#INTERNAL_ERROR}</li>
 * </ul>
 *
 * <p>Le eccezioni relative al routing (ente non censito, path non riconosciuto) generano
 * un SOAP Fault di tipo <strong>Client</strong> (errore del chiamante), mentre le eccezioni
 * di comunicazione con la PU generano un SOAP Fault di tipo <strong>Server</strong>.
 *
 * <p><strong>Namespace dinamico del fault detail</strong>: il namespace XML dell'elemento
 * {@code <errorCode>} nel detail viene determinato dinamicamente dall'endpoint invocato,
 * tramite {@link AbstractSoapProxyEndpoint#getFaultDetailNamespace()}. Questo garantisce
 * che i SOAP Fault rispettino il contratto WSDL specifico dell'endpoint (MyPay vs MyPivot).
 * In caso di errori pre-routing (quando l'endpoint non e' ancora determinato), viene usato
 * un namespace di fallback generico del middleware.
 *
 * <p>Tutti gli errori vengono loggati a livello ERROR per il monitoraggio.
 *
 * <p>Il resolver implementa {@link Ordered} con priorita' {@link Ordered#HIGHEST_PRECEDENCE}
 * per garantire che venga eseguito prima dei resolver di default di Spring WS
 * ({@code SoapFaultMappingExceptionResolver} e {@code SimpleSoapExceptionResolver}),
 * assicurando che il fault detail con {@code errorCode} sia sempre presente.
 */
@Component
public class SoapFaultExceptionResolver implements EndpointExceptionResolver, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SoapFaultExceptionResolver.class);

    /**
     * Namespace di fallback usato quando il fault viene generato prima che l'endpoint
     * sia stato determinato (es. errori di autenticazione a livello di infrastruttura).
     * Corrisponde al namespace generico MyPay (area piu' frequente).
     */
    private static final String FAULT_DETAIL_NAMESPACE_FALLBACK = Constants.NS_FAULT_MYPAY;

    /**
     * Priorita' massima per garantire che questo resolver venga eseguito prima di
     * quelli di default registrati da Spring WS.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public boolean resolveException(MessageContext messageContext, Object endpoint, Exception ex) {
        log.error("Errore nell'elaborazione della richiesta SOAP: [{}] {}",
                ex.getClass().getSimpleName(), ex.getMessage(), ex);

        try {
            SoapMessage response = (SoapMessage) messageContext.getResponse();
            SoapBody soapBody = response.getSoapBody();

            // Determina il namespace del fault detail in base all'endpoint invocato.
            // Se l'endpoint non e' ancora stato determinato (errore pre-routing), usa il fallback.
            String faultDetailNamespace = resolveFaultDetailNamespace(endpoint);

            SoapFault fault;

            if (ex instanceof EnteNonCensitoException enteEx) {
                // Ente non censito in mygov_ente → SOAP Fault Client (errore del chiamante)
                fault = soapBody.addClientOrSenderFault(
                        "Ente non autorizzato: codIpaEnte='" + enteEx.getCodIpaEnte()
                        + "'. L'ente non e' censito nel sistema (tabella mygov_ente).",
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, FaultCode.ENTE_NON_AUTORIZZATO, faultDetailNamespace);

            } else if (ex instanceof EnteNonIdentificabileException) {
                // Ente non identificabile dalla richiesta SOAP → SOAP Fault Client (errore del chiamante).
                // La richiesta manca degli identificatori necessari o il codice fiscale non e' censito.
                fault = soapBody.addClientOrSenderFault(
                        "Impossibile identificare l'ente dalla richiesta: " + ex.getMessage(),
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, FaultCode.ENTE_NON_IDENTIFICABILE, faultDetailNamespace);

            } else if (ex instanceof CredenzialeSilNonValidaException) {
                // Password SIL errata → SOAP Fault Client (errore del chiamante).
                // Il messaggio e' volutamente generico: non rivela se il problema e'
                // l'ente o la password, per non facilitare attacchi di enumerazione.
                fault = soapBody.addClientOrSenderFault(
                        "Credenziali non valide. Verificare il codice ente e la password.",
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, FaultCode.CREDENZIALI_NON_VALIDE, faultDetailNamespace);

            } else if (ex instanceof PathNonRiconosciutoException pathEx) {
                // Path non riconosciuto → SOAP Fault Client (errore del chiamante)
                fault = soapBody.addClientOrSenderFault(
                        "Path non riconosciuto: '" + pathEx.getRequestPath()
                        + "'. Nessun backend di destinazione configurato per questo path.",
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, FaultCode.PATH_NON_RICONOSCIUTO, faultDetailNamespace);

            } else if (ex instanceof PiattaformaAuthenticationException) {
                // Errore di autenticazione OAuth2 → SOAP Fault Server
                fault = soapBody.addServerOrReceiverFault(
                        "Errore di autenticazione verso la Piattaforma Unitaria: " + ex.getMessage(),
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, FaultCode.AUTH_ERROR, faultDetailNamespace);

            } else if (ex instanceof PiattaformaCommunicationException commEx) {
                // Errore di comunicazione con PU o backend → SOAP Fault Server.
                // Se il circuit breaker e' aperto, l'httpStatus e' 503 (non disponibile),
                // indipendentemente dall'errore originale che ha causato l'apertura del circuito.
                String detail = commEx.getHttpStatus() > 0
                        ? "HTTP " + commEx.getHttpStatus() + " - " + ex.getMessage()
                        : ex.getMessage();
                fault = soapBody.addServerOrReceiverFault(
                        "Errore di comunicazione con il backend: " + detail,
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, FaultCode.COMM_ERROR, faultDetailNamespace);

            } else {
                // Errore generico → SOAP Fault Server
                fault = soapBody.addServerOrReceiverFault(
                        "Errore interno del middleware: " + ex.getMessage(),
                        Locale.ITALIAN
                );
                addFaultDetailCode(fault, FaultCode.INTERNAL_ERROR, faultDetailNamespace);
            }

            return true;

        } catch (Exception resolverEx) {
            log.error("Errore durante la generazione del SOAP Fault: {}", resolverEx.getMessage(), resolverEx);
            return false;
        }
    }

    /**
     * Determina il namespace del fault detail in base all'endpoint Spring WS invocato.
     *
     * <p>Spring WS passa l'endpoint come {@link MethodEndpoint}. Da esso si estrae
     * il bean sottostante e, se e' un'istanza di {@link AbstractSoapProxyEndpoint},
     * si delega il namespace a {@link AbstractSoapProxyEndpoint#getFaultDetailNamespace()}.
     *
     * <p>Se l'endpoint non e' determinato (errore pre-routing, es. prima del dispatch)
     * o non e' un {@link AbstractSoapProxyEndpoint}, viene usato il namespace di fallback
     * {@link #FAULT_DETAIL_NAMESPACE_FALLBACK}.
     *
     * @param endpoint l'oggetto endpoint passato da Spring WS al resolver (puo' essere null)
     * @return il namespace URI del fault detail
     */
    private String resolveFaultDetailNamespace(Object endpoint) {
        if (endpoint instanceof MethodEndpoint methodEndpoint) {
            Object bean = methodEndpoint.getBean();
            if (bean instanceof AbstractSoapProxyEndpoint soapEndpoint) {
                return soapEndpoint.getFaultDetailNamespace();
            }
        }
        // Fallback: endpoint non determinato o non e' un AbstractSoapProxyEndpoint
        log.debug("Namespace fault detail non determinabile dall'endpoint '{}'. "
                + "Uso namespace di fallback: {}", endpoint, FAULT_DETAIL_NAMESPACE_FALLBACK);
        return FAULT_DETAIL_NAMESPACE_FALLBACK;
    }

    /**
     * Aggiunge l'elemento di dettaglio con il codice errore al SOAP Fault.
     *
     * @param fault          il SOAP Fault a cui aggiungere il dettaglio
     * @param faultCode      il codice errore strutturato da inserire
     * @param namespaceUri   il namespace URI da usare per l'elemento {@code errorCode}
     */
    private void addFaultDetailCode(SoapFault fault, FaultCode faultCode, String namespaceUri) {
        fault.addFaultDetail().addFaultDetailElement(
                new QName(namespaceUri, "errorCode")
        ).addText(faultCode.name());
    }
}
