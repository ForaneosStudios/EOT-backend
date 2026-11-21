package com.EOT.project.concurrency;

import com.EOT.project.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Opt-in "last edit made wins" for updates sent with {@code X-Client-Intent-At}: the moment the
 * user asked for the change, already converted to server time by the client.
 *
 * <p>When the header is absent (mobile app, game, older clients) nothing changes: the update is
 * applied as before. When present, an update made earlier than the resource's last saved change
 * is rejected as stale instead of overwriting a newer edit.
 */
public final class ClientIntent {
    public static final String HEADER = "X-Client-Intent-At";

    private ClientIntent() {
    }

    /** The intent time of the current request, or null when the client did not send one. */
    public static Instant current() {
        return current(Clock.systemUTC());
    }

    static Instant current(Clock clock) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return parse(attributes.getRequest(), clock);
    }

    static Instant parse(HttpServletRequest request, Clock clock) {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        Instant intent;
        try {
            intent = Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(HEADER + " debe ser una fecha ISO-8601 en UTC, por ejemplo 2026-11-21T18:00:00Z");
        }
        // A client clock running ahead must not make an edit win forever: cap it at "now".
        Instant now = clock.instant();
        return intent.isAfter(now) ? now : intent;
    }

    /**
     * True when an edit made at {@code intent} is older than the last saved change. Equal times
     * are not stale, so between edits made in the same instant the later arrival wins.
     */
    public static boolean isStale(Instant intent, Instant updatedAt, Instant createdAt) {
        if (intent == null) {
            return false;
        }
        Instant lastChange = updatedAt != null ? updatedAt : createdAt;
        return lastChange != null && intent.isBefore(lastChange);
    }
}
