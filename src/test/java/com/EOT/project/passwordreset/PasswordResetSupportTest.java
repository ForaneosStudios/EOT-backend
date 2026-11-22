package com.EOT.project.passwordreset;

import com.EOT.project.model.PasswordResetTokenModel;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.PasswordResetTokenRepository;
import com.EOT.project.repository.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetSupportTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void emailIsThemedAndEscapesTheName() {
        String html = PasswordResetMailer.render("<b>Ana</b>", "https://eot.test/auth/reset-password?token=a&b", 10);
        assertTrue(html.contains("ECHOES OF TIME"));
        assertTrue(html.contains("10 minutos"));
        assertTrue(html.contains("&lt;b&gt;Ana&lt;/b&gt;"));
        assertFalse(html.contains("<b>Ana</b>"));
        assertTrue(html.contains("token=a&amp;b"));
    }

    @Test
    void mailerReportsConfigurationAndDeliveryResult() {
        assertFalse(new PasswordResetMailer(provider(null), "a@b.c", "smtp.test", "").isConfigured());
        assertFalse(new PasswordResetMailer(provider(mock(JavaMailSender.class)), "a@b.c", " ", "").isConfigured());
        assertFalse(new PasswordResetMailer(provider(null), "a@b.c", "smtp.test", "").sendResetLink("x@y.z", "n", "l", 10));

        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        PasswordResetMailer mailer = new PasswordResetMailer(provider(sender), "a@b.c", "smtp.test", "");
        assertTrue(mailer.isConfigured());
        assertTrue(mailer.sendResetLink("x@y.z", "n", "https://l", 10));
        verify(sender).send(any(MimeMessage.class));

        doThrow(new MailSendException("down")).when(sender).send(any(MimeMessage.class));
        assertFalse(mailer.sendResetLink("x@y.z", "n", "https://l", 10));
    }

    @Test
    void supabaseClientRefusesToRunWithoutServiceKey() {
        SupabaseAdminClient client = new SupabaseAdminClient("https://x.supabase.co/auth/v1/", "");
        assertFalse(client.isConfigured());
        assertThrows(IllegalStateException.class, () -> client.resetPasswordAndRevokeSessions(UUID.randomUUID(), "a@b.c", "pw"));
        assertTrue(new SupabaseAdminClient("https://x.supabase.co/auth/v1", "key").isConfigured());
    }

    @Test
    void cleanupJobSwallowsFailures() {
        PasswordResetService service = mock(PasswordResetService.class);
        PasswordResetCleanupJob job = new PasswordResetCleanupJob(service);
        when(service.purgeExpired()).thenReturn(2);
        job.purge();
        when(service.purgeExpired()).thenThrow(new IllegalStateException("db"));
        assertDoesNotThrow(job::purge);
        doReturn(0).when(service).purgeExpired();
        assertDoesNotThrow(job::purge);
    }

    @Test
    void serviceHandlesDeletedAccountsAndBlankTokens() {
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        UserRepository users = mock(UserRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
        PasswordResetService service = new PasswordResetService(tokens, users, mock(PasswordResetMailer.class),
                mock(SupabaseAdminClient.class), new RequestRateLimiter(clock), "https://eot.test/", clock);

        assertFalse(service.isValid(null));
        assertFalse(service.isValid("  "));
        assertFalse(service.isValid("x".repeat(129)));

        UUID userId = UUID.randomUUID();
        PasswordResetTokenModel stored = PasswordResetTokenModel.builder().tokenId(UUID.randomUUID()).userId(userId)
                .tokenHash(PasswordResetService.hash("tok")).createdAt(clock.instant())
                .expiresAt(clock.instant().plusSeconds(60)).build();
        when(tokens.findByTokenHash(PasswordResetService.hash("tok"))).thenReturn(Optional.of(stored));
        when(tokens.markUsed(eq(stored.getTokenId()), any())).thenReturn(1);
        UserModel deleted = UserModel.builder().userId(userId).authUserId(UUID.randomUUID()).build();
        deleted.setIsDeleted(true);
        when(users.findById(userId)).thenReturn(Optional.of(deleted));
        assertThrows(com.EOT.project.exception.BadRequestException.class, () -> service.confirm("tok", "ClaveValida#1"));

        when(tokens.markUsed(eq(stored.getTokenId()), any())).thenReturn(0);
        assertThrows(com.EOT.project.exception.BadRequestException.class, () -> service.confirm("tok", "ClaveValida#1"));

        when(tokens.deleteExpiredBefore(any())).thenReturn(4);
        assertEquals(4, service.purgeExpired());
    }

    @Test
    void limiterBlocksAfterThePointsAreSpentAndReleasesWhenTheWindowPasses() {
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-22T10:00:00Z"));
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        RequestRateLimiter limiter = new RequestRateLimiter(clock);
        limiter.check("1.1.1.1");
        for (int i = 0; i < 3; i++) limiter.record("1.1.1.1", 2);
        var ex = assertThrows(com.EOT.project.exception.TooManyRequestsException.class, () -> limiter.check("1.1.1.1"));
        assertTrue(ex.getRetryAfterSeconds() > 0 && ex.getRetryAfterSeconds() <= 601);
        assertDoesNotThrow(() -> limiter.check("2.2.2.2"));

        now.set(now.get().plusSeconds(601));
        assertDoesNotThrow(() -> limiter.check("1.1.1.1"));
    }

    @Test
    void limiterEvictsIdleAddressesWhenTheTableIsFull() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        for (int i = 0; i < 10_001; i++) limiter.record("ip-" + i, 1);
        assertDoesNotThrow(() -> limiter.check("ip-1"));
    }

    @Test
    void brevoApiIsUsedWhenAKeyIsConfigured() {
        org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder();
        org.springframework.test.web.client.MockRestServiceServer server =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        PasswordResetMailer mailer = new PasswordResetMailer(provider(null), "Echoes of Time <no-reply@eot.test>", "", "key-123", builder.build());
        assertTrue(mailer.isConfigured());

        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("api-key", "key-123"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.sender.email").value("no-reply@eot.test"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.sender.name").value("Echoes of Time"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.to[0].email").value("x@y.z"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));
        assertTrue(mailer.sendResetLink("x@y.z", "Ana", "https://l", 10));
        server.verify();

        server.reset();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest());
        assertFalse(mailer.sendResetLink("x@y.z", "Ana", "https://l", 10));
    }

    @Test
    void brevoApiAcceptsAPlainSenderAddress() {
        org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder();
        org.springframework.test.web.client.MockRestServiceServer server =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        PasswordResetMailer mailer = new PasswordResetMailer(provider(null), "no-reply@eot.test", "", "key", builder.build());
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.sender.email").value("no-reply@eot.test"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.sender.name").value("Echoes of Time"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());
        assertTrue(mailer.sendResetLink("x@y.z", "Ana", "https://l", 10));
        server.verify();
    }
}
