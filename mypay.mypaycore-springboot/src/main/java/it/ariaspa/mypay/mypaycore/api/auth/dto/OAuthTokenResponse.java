package it.ariaspa.mypay.mypaycore.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    public OAuthTokenResponse() {
    }

    public OAuthTokenResponse(String accessToken, String tokenType, long expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    @Override
    public String toString() {
        return "OAuthTokenResponse{" +
                "tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", accessToken='[REDACTED]'" +
                '}';
    }
}
