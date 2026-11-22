package com.EOT.project.passwordreset;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to Supabase Auth with the service role key (server side only) to change a password and
 * revoke every refresh token of the account.
 */
@Component
public class SupabaseAdminClient {
    private final String authUrl;
    private final String serviceKey;
    private final RestClient http;

    public SupabaseAdminClient(@Value("${SUPABASE_AUTH_ISSUER:}") String issuer,
                               @Value("${SUPABASE_SERVICE_ROLE_KEY:}") String serviceKey) {
        this.authUrl = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        this.serviceKey = serviceKey;
        this.http = RestClient.create();
    }

    public boolean isConfigured() {
        return !authUrl.isBlank() && !serviceKey.isBlank();
    }

    /** Sets the new password, then signs in with it and signs out globally to kill every session. */
    public void resetPasswordAndRevokeSessions(UUID authUserId, String email, String newPassword) {
        if (!isConfigured()) {
            throw new IllegalStateException("Supabase service role is not configured");
        }
        try {
            http.put().uri(authUrl + "/admin/users/{id}", authUserId)
                    .header("apikey", serviceKey).header("Authorization", "Bearer " + serviceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("password", newPassword))
                    .retrieve().toBodilessEntity();
            Map<?, ?> session = http.post().uri(authUrl + "/token?grant_type=password")
                    .header("apikey", serviceKey).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", newPassword))
                    .retrieve().body(Map.class);
            Object accessToken = session == null ? null : session.get("access_token");
            if (accessToken == null) {
                throw new IllegalStateException("Supabase returned no session to revoke");
            }
            http.post().uri(authUrl + "/logout?scope=global")
                    .header("apikey", serviceKey).header("Authorization", "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException ex) {
            throw new IllegalStateException("Supabase Auth rejected the password reset", ex);
        }
    }
}
