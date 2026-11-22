package com.EOT.project.passwordreset;

import com.EOT.project.model.PasswordResetTokenModel;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.PasswordResetTokenRepository;
import com.EOT.project.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired RequestRateLimiter limiter;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PasswordResetMailer mailer;
    @MockitoBean SupabaseAdminClient supabase;

    UserModel user;

    @BeforeEach
    void setUp() {
        limiter.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = users.saveAndFlush(UserModel.builder().authUserId(UUID.randomUUID())
                .username("viajero_" + suffix).email("viajero-" + suffix + "@example.com").build());
        when(mailer.isConfigured()).thenReturn(true);
        when(mailer.sendResetLink(anyString(), anyString(), anyString(), anyInt())).thenReturn(true);
    }

    private String requestAndCaptureToken() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.expiresInMinutes").value(10));
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetLink(eq(user.getEmail()), eq(user.getUsername()), link.capture(), eq(10));
        String url = link.getValue();
        assertTrue(url.contains("/reset-password?token="));
        return url.substring(url.indexOf("token=") + 6);
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void unknownEmailIsReported() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@example.com\"}"))
                .andExpect(status().isNotFound());
        verify(mailer, never()).sendResetLink(anyString(), anyString(), anyString(), anyInt());
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAddressThatKeepsGuessingEmailsIsThrottled() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/password-reset/request").with(r -> { r.setRemoteAddr("9.9.9.9"); return r; })
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nadie" + i + "@example.com\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/auth/password-reset/request").with(r -> { r.setRemoteAddr("9.9.9.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        verify(mailer, never()).sendResetLink(anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void anAccountReceivesAtMostThreeEmailsPerHour() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            tokens.saveAndFlush(PasswordResetTokenModel.builder().userId(user.getUserId()).tokenHash("h" + i + user.getUserId())
                    .createdAt(now.minusSeconds(120L + i)).expiresAt(now.plusSeconds(60)).usedAt(now).build());
        }
        mvc.perform(post("/api/v1/auth/password-reset/request").with(r -> { r.setRemoteAddr("8.8.8.8"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "3600"));
    }

    @Test
    void tokenIsStoredHashedAndValidatedForTenMinutes() throws Exception {
        String token = requestAndCaptureToken();
        PasswordResetTokenModel stored = tokens.findFirstByUserIdOrderByCreatedAtDesc(user.getUserId()).orElseThrow();
        assertEquals(PasswordResetService.hash(token), stored.getTokenHash());
        assertEquals(600, stored.getExpiresAt().getEpochSecond() - stored.getCreatedAt().getEpochSecond());

        mvc.perform(get("/api/v1/auth/password-reset/validate").param("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        mvc.perform(get("/api/v1/auth/password-reset/validate").param("token", "otro"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void secondRequestWithinTheCooldownIsRejected() throws Exception {
        requestAndCaptureToken();
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void confirmChangesThePasswordClosesSessionsAndBurnsTheToken() throws Exception {
        String token = requestAndCaptureToken();
        Instant before = Instant.now().minusSeconds(2);

        confirm(token, "NuevaClave#2026").andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(true));

        verify(supabase).resetPasswordAndRevokeSessions(user.getAuthUserId(), user.getEmail(), "NuevaClave#2026");
        assertNotNull(users.findById(user.getUserId()).orElseThrow().getSessionsValidAfter());
        assertTrue(users.findById(user.getUserId()).orElseThrow().getSessionsValidAfter().isAfter(before));

        // The link works only once.
        confirm(token, "OtraClave#2026").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/password-reset/validate").param("token", token))
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void accessTokensIssuedBeforeTheResetAreRejected() throws Exception {
        String bearer = "old-session";
        when(decoder.decode(bearer)).thenReturn(Jwt.withTokenValue(bearer).header("alg", "RS256")
                .subject(user.getAuthUserId().toString()).issuedAt(Instant.now().minusSeconds(120))
                .expiresAt(Instant.now().plusSeconds(600)).build());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + bearer)).andExpect(status().isOk());

        user.setSessionsValidAfter(Instant.now().minusSeconds(30));
        users.saveAndFlush(user);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + bearer)).andExpect(status().isUnauthorized());

        String fresh = "new-session";
        when(decoder.decode(fresh)).thenReturn(Jwt.withTokenValue(fresh).header("alg", "RS256")
                .subject(user.getAuthUserId().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).build());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
    }

    @Test
    void expiredTokenAndWeakPasswordAreRejected() throws Exception {
        String token = requestAndCaptureToken();
        confirm(token, "corta").andExpect(status().isBadRequest());
        confirm(token, "sinmayuscula1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incluye al menos una mayúscula y un número."));
        confirm(token, "SinNumeroAqui").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incluye al menos una mayúscula y un número."));
        // A rejected password does not burn the link.
        mvc.perform(get("/api/v1/auth/password-reset/validate").param("token", token))
                .andExpect(jsonPath("$.valid").value(true));
        verify(supabase, never()).resetPasswordAndRevokeSessions(eq(user.getAuthUserId()), anyString(), anyString());

        PasswordResetTokenModel stored = tokens.findFirstByUserIdOrderByCreatedAtDesc(user.getUserId()).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        tokens.saveAndFlush(stored);
        confirm(token, "ClaveValida#1").andExpect(status().isBadRequest());
    }

    @Test
    void supabaseFailureKeepsTheLinkUsable() throws Exception {
        String token = requestAndCaptureToken();
        doThrow(new IllegalStateException("down")).when(supabase)
                .resetPasswordAndRevokeSessions(eq(user.getAuthUserId()), anyString(), anyString());
        confirm(token, "ClaveValida#1").andExpect(status().isBadGateway());
    }

    @Test
    void mailFailureReturnsServiceUnavailable() throws Exception {
        when(mailer.sendResetLink(anyString(), anyString(), anyString(), anyInt())).thenReturn(false);
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
