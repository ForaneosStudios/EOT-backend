package com.EOT.project.passwordreset;

import com.EOT.project.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limiter per client address (in memory, enough for a single instance).
 * Each request adds "points"; unknown emails cost more because they are what enumeration scripts send.
 */
@Component
public class RequestRateLimiter {
    static final int MAX_POINTS = 6;
    static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_TRACKED = 10_000;

    private record Hit(Instant at, int points) {}

    private final Map<String, Deque<Hit>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RequestRateLimiter() {
        this(Clock.systemUTC());
    }

    RequestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Fails with 429 when the address already used up its points in the window. */
    public void check(String key) {
        Deque<Hit> window = hits.get(key);
        if (window == null) {
            return;
        }
        synchronized (window) {
            Instant now = clock.instant();
            prune(window, now);
            int used = window.stream().mapToInt(Hit::points).sum();
            if (used >= MAX_POINTS) {
                long wait = Duration.between(now, window.peekFirst().at().plus(WINDOW)).toSeconds() + 1;
                throw new TooManyRequestsException("Demasiados intentos. Espera unos minutos antes de volver a intentarlo.", wait);
            }
        }
    }

    public void record(String key, int points) {
        if (hits.size() >= MAX_TRACKED) {
            Instant now = clock.instant();
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue(), now);
                    return e.getValue().isEmpty();
                }
            });
        }
        Deque<Hit> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(new Hit(clock.instant(), points));
        }
    }

    /** Forgets every address (used by tests that share the Spring context). */
    void clear() {
        hits.clear();
    }

    private static void prune(Deque<Hit> window, Instant now) {
        Iterator<Hit> it = window.iterator();
        while (it.hasNext() && it.next().at().plus(WINDOW).isBefore(now)) {
            it.remove();
        }
    }
}
