package it.ariaspa.mypay.mypaycore.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * DTO per la risposta dell'endpoint OAuth2 della Piattaforma Unitaria.
 *
 * Rappresenta il token JWT restituito dal Client Credentials Flow.
 * Esempio di risposta:
 * {
 *   "access_token": "eyJhbGci...",
 *   "token_type": "Bearer",
 *   "expires_in": 3600
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "accessToken") // accessToken escluso per sicurezza: evita l'esposizione nei log
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuthTokenResponse {

    /**
     * Il token di accesso JWT.
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * Il tipo di token (tipicamente "Bearer").
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * Durata di validita del token in secondi.
     */
    @JsonProperty("expires_in")
    private long expiresIn;
}
