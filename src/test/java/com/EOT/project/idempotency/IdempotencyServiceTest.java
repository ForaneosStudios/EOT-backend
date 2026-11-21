package com.EOT.project.idempotency;

import com.EOT.project.model.IdempotencyStatus;
import com.EOT.project.repository.IdempotencyKeyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceTest {
    @Autowired IdempotencyKeyRepository keys;
    @Autowired PlatformTransactionManager transactionManager;

    private MutableClock clock;
    private IdempotencyService service;
    private String sub;

    @BeforeEach
    void setup() {
        clock = new MutableClock(Instant.parse("2026-11-21T12:00:00Z"));
        service = new IdempotencyService(keys, transactionManager, clock);
        sub = UUID.randomUUID().toString();
    }

    private IdempotencyService.Decision begin(String key, String hash) {
        return service.begin(sub, key, "POST", "/api/v1/posts", hash);
    }

    @Test
    void concurrentDuplicateWaitsWhileFirstIsRunning() {
        assertEquals(IdempotencyService.Outcome.PROCEED, begin("key-running", "h1").outcome());
        assertEquals(IdempotencyService.Outcome.IN_PROGRESS, begin("key-running", "h1").outcome());
    }

    @Test
    void completedRequestIsReplayed() {
        var first = begin("key-done", "h1");
        assertTrue(service.complete(first.entry().getId(), first.lease(), new IdempotencyService.StoredResponse(201, "{\"id\":1}", "application/json", null)));
        var replay = begin("key-done", "h1");
        assertEquals(IdempotencyService.Outcome.REPLAY, replay.outcome());
        assertEquals(201, replay.entry().getResponseStatus());
        assertEquals("{\"id\":1}", replay.entry().getResponseBody());
        assertEquals(IdempotencyStatus.COMPLETED, replay.entry().getStatus());
    }

    @Test
    void releasedKeyCanRunAgain() {
        var first = begin("key-released", "h1");
        service.release(first.entry().getId(), first.lease());
        assertEquals(IdempotencyService.Outcome.PROCEED, begin("key-released", "h1").outcome());
    }

    @Test
    void staleLeaseIsTakenOverOnce() {
        begin("key-stale", "h1");
        clock.advance(IdempotencyService.LEASE.plusSeconds(1));
        assertEquals(IdempotencyService.Outcome.PROCEED, begin("key-stale", "h1").outcome());
        assertEquals(IdempotencyService.Outcome.IN_PROGRESS, begin("key-stale", "h1").outcome());
    }

    @Test
    void expiredRecordsAreTreatedAsNewAndPurged() {
        var first = begin("key-expired", "h1");
        service.complete(first.entry().getId(), first.lease(), new IdempotencyService.StoredResponse(200, null, null, null));
        clock.advance(IdempotencyService.TTL.plusMinutes(1));
        assertEquals(IdempotencyService.Outcome.PROCEED, begin("key-expired", "other-hash").outcome());

        begin("key-to-purge", "h1");
        clock.advance(IdempotencyService.TTL.plusMinutes(1));
        assertTrue(service.deleteExpired() >= 1);
        assertTrue(keys.findByAuthSubAndIdemKey(sub, "key-to-purge").isEmpty());
    }

    @Test
    void requestThatLostItsLeaseCannotStoreOrReleaseTheKey() {
        var original = begin("key-lost", "h1");
        clock.advance(IdempotencyService.LEASE.plusSeconds(1));
        var takeover = begin("key-lost", "h1");
        assertEquals(IdempotencyService.Outcome.PROCEED, takeover.outcome());

        // The slow original must not store its result nor free the key the retry now owns.
        assertFalse(service.complete(original.entry().getId(), original.lease(), new IdempotencyService.StoredResponse(201, "{}", null, null)));
        service.release(original.entry().getId(), original.lease());
        assertEquals(IdempotencyService.Outcome.IN_PROGRESS, begin("key-lost", "h1").outcome());

        assertTrue(service.complete(takeover.entry().getId(), takeover.lease(), new IdempotencyService.StoredResponse(201, "{}", null, null)));
        assertEquals(IdempotencyService.Outcome.REPLAY, begin("key-lost", "h1").outcome());
    }

    @Test
    void completedRequestIsNeverTakenOver() {
        var first = begin("key-finished", "h1");
        service.complete(first.entry().getId(), first.lease(), new IdempotencyService.StoredResponse(201, "{}", null, null));
        clock.advance(IdempotencyService.LEASE.plusMinutes(5));
        assertEquals(IdempotencyService.Outcome.REPLAY, begin("key-finished", "h1").outcome());
    }

    @Test
    void differentHashIsMismatch() {
        begin("key-hash", "h1");
        assertEquals(IdempotencyService.Outcome.MISMATCH, begin("key-hash", "h2").outcome());
    }

    @Test
    void storesOnlySuccessAndDeterministicClientErrors() {
        assertTrue(IdempotencyFilter.isStorable(200));
        assertTrue(IdempotencyFilter.isStorable(201));
        assertTrue(IdempotencyFilter.isStorable(204));
        assertTrue(IdempotencyFilter.isStorable(404));
        assertTrue(IdempotencyFilter.isStorable(409));
        assertFalse(IdempotencyFilter.isStorable(401));
        assertFalse(IdempotencyFilter.isStorable(403));
        assertFalse(IdempotencyFilter.isStorable(429));
        assertFalse(IdempotencyFilter.isStorable(500));
        assertFalse(IdempotencyFilter.isStorable(503));
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
