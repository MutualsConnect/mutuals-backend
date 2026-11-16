package com.mutuals.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.mutuals.common.exception.InvalidOperationException;
import com.mutuals.common.exception.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Verifica el ID token de Google: firma (claves públicas de Google), emisor, expiración
 * y que la audiencia sea nuestro Client ID.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${mutuals.google.client-id:}") String clientId) {
        this.verifier = clientId == null || clientId.isBlank()
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(List.of(clientId.trim()))
                        .build();
    }

    public GoogleUser verify(String idToken) {
        if (verifier == null) {
            throw new InvalidOperationException("Google login is not configured");
        }
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            log.warn("Could not verify Google ID token: {}", ex.getMessage());
            token = null;
        }
        if (token == null) {
            throw new InvalidTokenException("Invalid Google token");
        }
        GoogleIdToken.Payload payload = token.getPayload();
        if (payload.getEmail() == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidTokenException("Google account email is not verified");
        }
        return new GoogleUser(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture"));
    }

    public record GoogleUser(String subject, String email, String name, String picture) {
    }
}
