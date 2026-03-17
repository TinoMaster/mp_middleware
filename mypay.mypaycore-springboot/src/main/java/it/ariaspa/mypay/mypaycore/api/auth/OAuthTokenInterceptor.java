package it.ariaspa.mypay.mypaycore.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Interceptor HTTP che aggiunge automaticamente il token OAuth2 Bearer
 * alle richieste in uscita verso la Piattaforma Unitaria.
 *
 * Viene utilizzato dal RestTemplate configurato nel PiattaformaUnitariaClient
 * per garantire che ogni richiesta verso la piattaforma sia autenticata.
 *
 * Il token viene ottenuto dal OAuthTokenService, che gestisce
 * la cache e il refresh automatico.
 */
@Component
public class OAuthTokenInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenInterceptor.class);

    private final OAuthTokenService oAuthTokenService;

    public OAuthTokenInterceptor(OAuthTokenService oAuthTokenService) {
        this.oAuthTokenService = oAuthTokenService;
    }

    /**
     * Intercetta la richiesta HTTP e aggiunge l'header Authorization con il token Bearer.
     *
     * @param request   la richiesta HTTP in uscita
     * @param body      il corpo della richiesta
     * @param execution l'esecuzione della catena di interceptor
     * @return la risposta HTTP
     * @throws IOException in caso di errore I/O
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {

        String token = oAuthTokenService.getAccessToken();
        request.getHeaders().setBearerAuth(token);

        log.debug("Token Bearer aggiunto alla richiesta verso: {}", request.getURI());

        return execution.execute(request, body);
    }
}
