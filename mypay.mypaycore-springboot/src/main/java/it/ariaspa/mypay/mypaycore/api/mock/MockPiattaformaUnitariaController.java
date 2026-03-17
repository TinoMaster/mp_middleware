package it.ariaspa.mypay.mypaycore.api.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Mock server della Piattaforma Unitaria (pagoPA) — attivo SOLO con profilo "local".
 *
 * Simula:
 * 1. /mock/pu/auth/oauth/token        → OAuth2 Client Credentials token endpoint
 * 2. /mock/pu/sil/soap/**             → SOAP endpoint di riconciliazione
 *
 * In application-local.yml la proprietà piattaforma-unitaria.base-url
 * viene sovrascritta con http://localhost:8080/mock, in modo che il middleware
 * chiami questo mock invece dell'ambiente UAT reale.
 *
 * FLUSSO COMPLETO TESTABILE:
 *   Postman/SoapUI → POST /pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati
 *      └─ ReconciliationEndpoint
 *           └─ PiattaformaUnitariaClient → POST /mock/pu/auth/oauth/token  (se token non cached)
 *           └─ PiattaformaUnitariaClient → POST /mock/pu/sil/soap/reconciliation/...
 *                └─ risposta SOAP di esempio
 */
@RestController
@RequestMapping("/mock")
@Profile("local")
public class MockPiattaformaUnitariaController {

    private static final Logger log = LoggerFactory.getLogger(MockPiattaformaUnitariaController.class);

    // Token fisso usato dal mock: qualsiasi client_id/client_secret viene accettato
    private static final String MOCK_ACCESS_TOKEN = "mock-access-token-" + UUID.randomUUID();
    private static final int TOKEN_EXPIRES_IN = 3600;

    // -------------------------------------------------------------------------
    // 1. OAuth2 Token Endpoint mock
    // -------------------------------------------------------------------------

    /**
     * Simula POST /pu/auth/oauth/token
     *
     * Accetta qualsiasi client_id/client_secret e restituisce un token JWT fittizio.
     * Registra nel log i parametri ricevuti per facilitare il debugging.
     */
    @PostMapping(
            value = "/pu/auth/oauth/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> mockOAuthToken(
            @RequestParam MultiValueMap<String, String> params) {

        String clientId = params.getFirst("client_id");
        String grantType = params.getFirst("grant_type");
        String scope = params.getFirst("scope");

        log.info("[MOCK OAuth2] Richiesta token ricevuta. client_id={}, grant_type={}, scope={}",
                clientId, grantType, scope);

        Map<String, Object> response = Map.of(
                "access_token", MOCK_ACCESS_TOKEN,
                "token_type", "Bearer",
                "expires_in", TOKEN_EXPIRES_IN,
                "scope", scope != null ? scope : "openid",
                "issued_at", Instant.now().getEpochSecond()
        );

        log.info("[MOCK OAuth2] Token emesso con successo per client_id={}", clientId);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // 2. SOAP Endpoint mock — riconciliazione
    // -------------------------------------------------------------------------

    /**
     * Simula POST /pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati
     *
     * Riceve il payload SOAP inoltrato dal middleware e restituisce una risposta
     * SOAP di esempio con codice di successo.
     */
    @PostMapping(
            value = "/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati",
            consumes = {"text/xml", "application/xml", "application/soap+xml"},
            produces = "text/xml"
    )
    public ResponseEntity<String> mockReconciliationSoap(
            @RequestBody String soapBody,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        log.info("[MOCK SOAP] Richiesta di riconciliazione ricevuta.");
        log.info("[MOCK SOAP] Authorization header presente: {}", authHeader != null);
        log.debug("[MOCK SOAP] Payload ricevuto:\n{}", soapBody);

        String responseXml = buildMockSoapResponse(soapBody);

        log.info("[MOCK SOAP] Risposta di successo inviata.");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/xml;charset=UTF-8"))
                .body(responseXml);
    }

    // -------------------------------------------------------------------------
    // 3. Endpoint di diagnostica
    // -------------------------------------------------------------------------

    /**
     * GET /mock/status — verifica che il mock sia attivo.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "profile", "local",
                "description", "Mock Piattaforma Unitaria attivo",
                "endpoints", Map.of(
                        "oauth2Token", "POST /mock/pu/auth/oauth/token",
                        "reconciliation", "POST /mock/pu/sil/soap/reconciliation/PagamentiTelematiciPagatiRiconciliati"
                )
        ));
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private String buildMockSoapResponse(String requestBody) {
        // Estrae codIpaEnte dalla richiesta se presente (per includerlo nella risposta mock)
        String codIpaEnte = extractTagValue(requestBody, "codIpaEnte", "UNKNOWN_ENTE");
        String tipoFlusso = extractTagValue(requestBody, "tipoFlusso", "O");

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope
                    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:ente="http://www.regione.lombardia.it/mypay/ente">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <ente:pivotSILAutorizzaImportFlussoTesoreria_RPT_risposta>
                            <codIpaEnte>%s</codIpaEnte>
                            <tipoFlusso>%s</tipoFlusso>
                            <codiceEsito>0</codiceEsito>
                            <descrizioneEsito>Operazione completata con successo (MOCK)</descrizioneEsito>
                            <identificativoFlusso>MOCK-%s</identificativoFlusso>
                        </ente:pivotSILAutorizzaImportFlussoTesoreria_RPT_risposta>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(codIpaEnte, tipoFlusso, UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private String extractTagValue(String xml, String tagName, String defaultValue) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int start = xml.indexOf(openTag);
        int end = xml.indexOf(closeTag);
        if (start >= 0 && end > start) {
            return xml.substring(start + openTag.length(), end).trim();
        }
        // Prova anche con namespace prefix (es. <ns:codIpaEnte>)
        int colonIdx = xml.indexOf(":" + tagName + ">");
        if (colonIdx > 0) {
            int nsStart = xml.lastIndexOf("<", colonIdx);
            String fullOpenTag = xml.substring(nsStart, colonIdx + tagName.length() + 2);
            String fullCloseTag = "</" + xml.substring(nsStart + 1, colonIdx + 1) + tagName + ">";
            int s = xml.indexOf(fullOpenTag);
            int e = xml.indexOf(fullCloseTag);
            if (s >= 0 && e > s) {
                return xml.substring(s + fullOpenTag.length(), e).trim();
            }
        }
        return defaultValue;
    }
}
