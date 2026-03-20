package it.ariaspa.mypay.mypaycore.api.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;

import java.util.List;

/**
 * Configurazione Spring WS per esporre endpoint SOAP server-side.
 * <p>
 * Registra un {@link MessageDispatcherServlet} mappato sull'URL base {@code /ws/*}
 * che gestisce tutte le richieste SOAP in ingresso dai sistemi SIL.
 * <p>
 * I path esposti dal middleware replicano quelli dei backend originali (mypay, mypivot):
 * <ul>
 *   <li>{@code /ws/pivot/*} — operazioni di riconciliazione (mypivot)</li>
 *   <li>{@code /ws/pa/*} — operazioni di pagamento (mypay)</li>
 *   <li>{@code /ws/fesp/*} — operazioni FESP (mypay)</li>
 * </ul>
 * <p>
 * I SIL non devono modificare nulla: il middleware si interpone in modo trasparente.
 * Gli endpoint SOAP vengono automaticamente rilevati tramite {@code @Endpoint}.
 */
@EnableWs
@Configuration
public class SoapWebServiceConfig implements WsConfigurer {

    /**
     * Registra il MessageDispatcherServlet per gestire le richieste SOAP.
     * <p>
     * Mappato su {@code /ws/*} per intercettare tutti i path SOAP che i SIL inviano
     * ai backend originali. Il path precedente ({@code /pu/sil/soap/*}) era provvisorio
     * ed e stato sostituito con i path reali dei backend in Fase 5.
     *
     * @param applicationContext il contesto Spring
     * @return bean di registrazione del servlet
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {

        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);

        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        // Interceptor personalizzati possono essere aggiunti qui in fasi successive
    }
}
