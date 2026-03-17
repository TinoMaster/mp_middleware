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
 *
 * Registra un MessageDispatcherServlet mappato sull'URL base /pu/sil/soap/*
 * che gestisce tutte le richieste SOAP in ingresso dai sistemi SIL.
 *
 * Gli endpoint SOAP vengono automaticamente rilevati tramite @Endpoint.
 */
@EnableWs
@Configuration
public class SoapWebServiceConfig implements WsConfigurer {

    /**
     * Registra il MessageDispatcherServlet per gestire le richieste SOAP.
     * Mappato su /pu/sil/soap/* come specificato nei requisiti.
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

        return new ServletRegistrationBean<>(servlet, "/pu/sil/soap/*");
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        // Interceptor personalizzati possono essere aggiunti qui in fasi successive
    }
}
