package com.EOT.project.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the open Server-Sent Events streams of each player and pushes game updates to them.
 * In memory: enough for the single Render instance; several instances would need a shared bus.
 */
@Component
public class LiveEventHub {
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);
    static final int MAX_STREAMS_PER_USER = 5;
    private static final Logger log = LoggerFactory.getLogger(LiveEventHub.class);

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    /** Opens a stream for the player; the client reconnects when it times out. */
    public SseEmitter subscribe(UUID authUserId) {
        SseEmitter emitter = createEmitter();
        Set<SseEmitter> userStreams = streams.computeIfAbsent(authUserId, id -> new CopyOnWriteArraySet<>());
        if (userStreams.size() >= MAX_STREAMS_PER_USER) {
            SseEmitter oldest = userStreams.iterator().next();
            userStreams.remove(oldest);
            oldest.complete();
        }
        userStreams.add(emitter);
        Runnable cleanup = () -> remove(authUserId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        send(authUserId, emitter, SseEmitter.event().name("ready").data(Map.of()));
        return emitter;
    }

    SseEmitter createEmitter() {
        return new SseEmitter(STREAM_TIMEOUT.toMillis());
    }

    /** Delivered only once the change is committed, so a client that refetches sees it. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLiveEvent(LiveEvent event) {
        if (event.authUserId() == null) {
            streams.forEach((user, userStreams) -> userStreams.forEach(emitter -> send(user, emitter, toSse(event))));
        } else {
            streams.getOrDefault(event.authUserId(), Set.of()).forEach(emitter -> send(event.authUserId(), emitter, toSse(event)));
        }
    }

    /** Keeps proxies from closing idle connections and drops streams whose client went away. */
    @Scheduled(initialDelayString = "PT25S", fixedDelayString = "PT25S")
    public void heartbeat() {
        streams.forEach((user, userStreams) -> userStreams.forEach(emitter -> send(user, emitter, SseEmitter.event().comment("ping"))));
    }

    int openStreams() {
        return streams.values().stream().mapToInt(Set::size).sum();
    }

    private static SseEmitter.SseEventBuilder toSse(LiveEvent event) {
        return SseEmitter.event().name(event.type()).data(event.data());
    }

    private void send(UUID user, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException ex) {
            log.debug("Dropping a closed live stream: {}", ex.getMessage());
            remove(user, emitter);
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // already completed
            }
        }
    }

    private void remove(UUID user, SseEmitter emitter) {
        streams.computeIfPresent(user, (id, userStreams) -> {
            userStreams.remove(emitter);
            return userStreams.isEmpty() ? null : userStreams;
        });
    }

    /** Snapshot used by tests. */
    List<SseEmitter> streamsOf(UUID user) {
        return new ArrayList<>(streams.getOrDefault(user, Set.of()));
    }
}
