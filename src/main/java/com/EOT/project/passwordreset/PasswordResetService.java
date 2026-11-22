package com.EOT.project.passwordreset;

import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.exception.TooManyRequestsException;
import com.EOT.project.model.PasswordResetTokenModel;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.PasswordResetTokenRepository;
import com.EOT.project.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {
    public static final Duration TTL = Duration.ofMinutes(10);
    static final Duration COOLDOWN = Duration.ofSeconds(60);
    static final Duration HOURLY_WINDOW = Duration.ofHours(1);
    static final int MAX_PER_HOUR = 3;
    static final int UNKNOWN_EMAIL_COST = 2;
    static final int MIN_PASSWORD = 8;
    static final int MAX_PASSWORD = 72;

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordResetMailer mailer;
    private final SupabaseAdminClient supabase;
    private final RequestRateLimiter limiter;
    private final String frontendUrl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
                                PasswordResetMailer mailer, SupabaseAdminClient supabase,
                                RequestRateLimiter limiter,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this(tokens, users, mailer, supabase, limiter, frontendUrl, Clock.systemUTC());
    }

    PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, PasswordResetMailer mailer,
                         SupabaseAdminClient supabase, RequestRateLimiter limiter, String frontendUrl, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.mailer = mailer;
        this.supabase = supabase;
        this.limiter = limiter;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.clock = clock;
    }

    /** Creates a token and emails the link. Unknown emails are reported on purpose (product decision). */
    @Transactional
    public void requestReset(String rawEmail, String clientKey) {
        limiter.check(clientKey);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        UserModel user = users.findByEmailAndIsDeletedFalse(email)
                .filter(u -> u.getAuthUserId() != null)
                .orElse(null);
        if (user == null) {
            limiter.record(clientKey, UNKNOWN_EMAIL_COST);
            throw new ResourceNotFoundException("No existe una cuenta con ese correo");
        }
        limiter.record(clientKey, 1);
        Instant now = clock.instant();
        if (tokens.countByUserIdAndCreatedAtAfter(user.getUserId(), now.minus(HOURLY_WINDOW)) >= MAX_PER_HOUR) {
            throw new TooManyRequestsException(
                    "Esta cuenta ya recibió demasiados correos de recuperación. Inténtalo de nuevo en una hora.",
                    HOURLY_WINDOW.toSeconds());
        }
        tokens.findFirstByUserIdOrderByCreatedAtDesc(user.getUserId())
                .filter(last -> last.getCreatedAt().plus(COOLDOWN).isAfter(now))
                .ifPresent(last -> {
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "Ya enviamos un correo hace un momento. Espera un minuto para pedir otro.");
                });
        if (!mailer.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El envío de correos no está disponible");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.invalidatePending(user.getUserId(), now);
        tokens.save(PasswordResetTokenModel.builder().userId(user.getUserId()).tokenHash(hash(token))
                .createdAt(now).expiresAt(now.plus(TTL)).build());
        String link = frontendUrl + "/reset-password?token=" + token;
        if (!mailer.sendResetLink(user.getEmail(), user.getUsername(), link, (int) TTL.toMinutes())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el correo. Inténtalo más tarde.");
        }
    }

    /** True while the link can still be used (lets the UI reject dead links before asking for a password). */
    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return findUsable(token).isPresent();
    }

    /** Changes the password, closes every session and burns the token, all or nothing. */
    @Transactional
    public void confirm(String token, String newPassword) {
        validatePassword(newPassword);
        PasswordResetTokenModel stored = findUsable(token).orElseThrow(PasswordResetService::invalidLink);
        Instant now = clock.instant();
        if (tokens.markUsed(stored.getTokenId(), now) != 1) {
            throw invalidLink();
        }
        UserModel user = users.findById(stored.getUserId()).filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                .filter(u -> u.getAuthUserId() != null).orElseThrow(PasswordResetService::invalidLink);
        user.setSessionsValidAfter(now.truncatedTo(ChronoUnit.SECONDS));
        users.saveAndFlush(user);
        try {
            supabase.resetPasswordAndRevokeSessions(user.getAuthUserId(), user.getEmail(), newPassword);
        } catch (IllegalStateException ex) {
            // Rolls everything back, so the link keeps working until it expires.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo actualizar la contraseña. Inténtalo de nuevo con el mismo enlace.", ex);
        }
    }

    @Transactional
    public int purgeExpired() {
        return tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }

    private Optional<PasswordResetTokenModel> findUsable(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHash(hash(token.trim()))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now));
    }

    /** Same rules as sign-up in the apps: 8 to 72 characters, at least one uppercase letter and one digit. */
    static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new BadRequestException("Debe tener al menos " + MIN_PASSWORD + " caracteres.");
        }
        if (password.length() > MAX_PASSWORD) {
            throw new BadRequestException("No puede superar " + MAX_PASSWORD + " caracteres.");
        }
        if (password.chars().noneMatch(c -> c >= 'A' && c <= 'Z') || password.chars().noneMatch(c -> c >= '0' && c <= '9')) {
            throw new BadRequestException("Incluye al menos una mayúscula y un número.");
        }
    }

    private static BadRequestException invalidLink() {
        return new BadRequestException("El enlace no es válido o ya expiró. Solicita uno nuevo.");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
