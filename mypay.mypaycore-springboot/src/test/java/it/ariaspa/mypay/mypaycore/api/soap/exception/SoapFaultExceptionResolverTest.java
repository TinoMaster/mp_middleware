package it.ariaspa.mypay.mypaycore.api.soap.exception;

import it.ariaspa.mypay.mypaycore.api.common.exception.EnteNonCensitoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PathNonRiconosciutoException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaAuthenticationException;
import it.ariaspa.mypay.mypaycore.api.common.exception.PiattaformaCommunicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.SoapMessage;

import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test unitari per il {@link SoapFaultExceptionResolver}.
 *
 * <p>Verifica il mapping corretto di ogni tipo di eccezione in un SOAP Fault
 * con il codice di errore appropriato.
 */
@ExtendWith(MockitoExtension.class)
class SoapFaultExceptionResolverTest {

    @Mock
    private MessageContext messageContext;

    @Mock
    private SoapMessage soapResponse;

    @Mock
    private SoapBody soapBody;

    @Mock
    private SoapFault soapFault;

    @Mock
    private SoapFaultDetail faultDetail;

    @Mock
    private SoapFaultDetailElement detailElement;

    private SoapFaultExceptionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SoapFaultExceptionResolver();
        when(messageContext.getResponse()).thenReturn(soapResponse);
        when(soapResponse.getSoapBody()).thenReturn(soapBody);
        // lenient perche' non tutti i test raggiungono il codice che aggiunge il detail
        // (es. resolveException_erroreNelResolver_restituisceFalse lancia prima)
        lenient().when(soapFault.addFaultDetail()).thenReturn(faultDetail);
        lenient().when(faultDetail.addFaultDetailElement(any(QName.class))).thenReturn(detailElement);
    }

    @Test
    @DisplayName("EnteNonCensitoException → SOAP Fault Client con ENTE_NON_AUTORIZZATO")
    void resolveException_enteNonCensito_clientFault() {
        when(soapBody.addClientOrSenderFault(anyString(), any())).thenReturn(soapFault);

        EnteNonCensitoException ex = new EnteNonCensitoException("R_LOMBARDIA", "pivotSILAutorizzaImportFlussoTesoreria");

        boolean result = resolver.resolveException(messageContext, null, ex);

        assertTrue(result);
        verify(soapBody).addClientOrSenderFault(contains("R_LOMBARDIA"), any());
        verify(detailElement).addText("ENTE_NON_AUTORIZZATO");
    }

    @Test
    @DisplayName("PathNonRiconosciutoException → SOAP Fault Client con PATH_NON_RICONOSCIUTO")
    void resolveException_pathNonRiconosciuto_clientFault() {
        when(soapBody.addClientOrSenderFault(anyString(), any())).thenReturn(soapFault);

        PathNonRiconosciutoException ex = new PathNonRiconosciutoException("/ws/sconosciuto/Servizio");

        boolean result = resolver.resolveException(messageContext, null, ex);

        assertTrue(result);
        verify(soapBody).addClientOrSenderFault(contains("/ws/sconosciuto/Servizio"), any());
        verify(detailElement).addText("PATH_NON_RICONOSCIUTO");
    }

    @Test
    @DisplayName("PiattaformaAuthenticationException → SOAP Fault Server con AUTH_ERROR")
    void resolveException_authError_serverFault() {
        when(soapBody.addServerOrReceiverFault(anyString(), any())).thenReturn(soapFault);

        PiattaformaAuthenticationException ex = new PiattaformaAuthenticationException("Token scaduto");

        boolean result = resolver.resolveException(messageContext, null, ex);

        assertTrue(result);
        verify(soapBody).addServerOrReceiverFault(contains("Token scaduto"), any());
        verify(detailElement).addText("AUTH_ERROR");
    }

    @Test
    @DisplayName("PiattaformaCommunicationException → SOAP Fault Server con COMM_ERROR")
    void resolveException_commError_serverFault() {
        when(soapBody.addServerOrReceiverFault(anyString(), any())).thenReturn(soapFault);

        PiattaformaCommunicationException ex = new PiattaformaCommunicationException("Timeout", 504);

        boolean result = resolver.resolveException(messageContext, null, ex);

        assertTrue(result);
        verify(soapBody).addServerOrReceiverFault(contains("HTTP 504"), any());
        verify(detailElement).addText("COMM_ERROR");
    }

    @Test
    @DisplayName("PiattaformaCommunicationException senza HTTP status → SOAP Fault senza HTTP prefix")
    void resolveException_commErrorSenzaHttpStatus_serverFault() {
        when(soapBody.addServerOrReceiverFault(anyString(), any())).thenReturn(soapFault);

        PiattaformaCommunicationException ex = new PiattaformaCommunicationException("Connessione rifiutata");

        boolean result = resolver.resolveException(messageContext, null, ex);

        assertTrue(result);
        verify(soapBody).addServerOrReceiverFault(contains("Connessione rifiutata"), any());
        verify(detailElement).addText("COMM_ERROR");
    }

    @Test
    @DisplayName("RuntimeException generica → SOAP Fault Server con INTERNAL_ERROR")
    void resolveException_genericException_serverFault() {
        when(soapBody.addServerOrReceiverFault(anyString(), any())).thenReturn(soapFault);

        RuntimeException ex = new RuntimeException("Errore imprevisto");

        boolean result = resolver.resolveException(messageContext, null, ex);

        assertTrue(result);
        verify(soapBody).addServerOrReceiverFault(contains("Errore imprevisto"), any());
        verify(detailElement).addText("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Errore nel resolver → restituisce false")
    void resolveException_erroreNelResolver_restituisceFalse() {
        when(soapBody.addServerOrReceiverFault(anyString(), any())).thenThrow(new RuntimeException("Errore nel resolver"));

        boolean result = resolver.resolveException(messageContext, null, new RuntimeException("Test"));

        assertFalse(result);
    }

    @Test
    @DisplayName("EnteNonCensitoException contiene codIpaEnte e tipoOperazione nel messaggio SOAP Fault")
    void resolveException_enteNonCensito_contieneDettagliNelMessaggio() {
        when(soapBody.addClientOrSenderFault(anyString(), any())).thenReturn(soapFault);

        EnteNonCensitoException ex = new EnteNonCensitoException("COMUNE_BERGAMO", "paVerificaRPT");

        resolver.resolveException(messageContext, null, ex);

        // Verifica che il messaggio SOAP Fault contenga sia codIpaEnte che tipoOperazione
        verify(soapBody).addClientOrSenderFault(
                argThat(msg -> msg.contains("COMUNE_BERGAMO") && msg.contains("paVerificaRPT")),
                any()
        );
    }
}
