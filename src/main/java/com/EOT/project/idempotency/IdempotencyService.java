package com.EOT.project.idempotency;

import com.EOT.project.model.IdempotencyKeyModel;
import com.EOT.project.model.IdempotencyStatus;
import com.EOT.project.repository.IdempotencyKeyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bookkeeping for opt-in idempotent mutations.
 *
 * <p>Claiming a key ({@link #begin}) and forgetting it ({@link #release}) run in their own
 * transactions so concurrent retries see the key immediately. Storing a successful response
 * ({@link #completeInCurrentTransaction}) runs inside the business transaction, so the change and
 * its stored response are committed together: after a crash either both exist or neither does.
 *
 * <p>A request owns its key while the lease it was given is unchanged. If it runs past the lease
 * and a retry takes over, the original can no longer store its result and must roll back, so the
 * action is never applied twice.
 */
@Service
public class IdempotencyService {
    /** How long a request may stay IN_PROGRESS before a retry may take it over (e.g. after a crash). */
    static final Duration LEASE = Duration.ofSeconds(60);
    /** How long a completed response is kept for replay. */
    static final Duration TTL = Duration.ofHours(24);

    public enum Outcome { PROCEED, REPLAY, IN_PROGRESS, MISMATCH }

    /** Result of claiming a key. {@code lease} identifies this request's ownership when PROCEED. */
    /** The parts of a response that are stored for replay. */
    public record StoredResponse(int status, String body, String contentType, String location) { }

    public record Decision(Outcome outcome, IdempotencyKeyModel entry, Instant lease) {
        Decision(Outcome outcome, IdempotencyKeyModel entry) {
            this(outcome, entry, entry == null ? null : entry.getLockedUntil());
        }
    }

    private final IdempotencyKeyRepository keys;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public IdempotencyService(IdempotencyKeyRepository keys, PlatformTransactionManager transactionManager) {
        this(keys, transactionManager, Clock.systemUTC());
    }

    IdempotencyService(IdempotencyKeyRepository keys, PlatformTransactionManager transactionManager, Clock clock) {
        this.keys = keys;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    // Millisecond precision so the lease compares equal after a round trip through any database.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    public Decision begin(String authSub, String key, String method, String path, String requestHash) {
        try {
            return tx.execute(status -> beginInTransaction(authSub, key, method, path, requestHash));
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Another request with the same key inserted first; judge against its record.
            return tx.execute(status -> keys.findByAuthSubAndIdemKey(authSub, key)
                    .map(existing -> evaluate(existing, requestHash, now()))
                    .orElseGet(() -> new Decision(Outcome.IN_PROGRESS, null)));
        }
    }

    private Decision beginInTransaction(String authSub, String key, String method, String path, String requestHash) {
        Instant now = now();
        var existing = keys.findByAuthSubAndIdemKey(authSub, key);
        if (existing.isPresent()) {
            if (existing.get().getExpiresAt().isAfter(now)) {
                return evaluate(existing.get(), requestHash, now);
            }
            keys.delete(existing.get());
            keys.flush();
        }
        IdempotencyKeyModel created = keys.saveAndFlush(IdempotencyKeyModel.builder()
                .authSub(authSub)
                .idemKey(key)
                .requestMethod(method)
                .requestPath(truncate(path, 512))
                .requestHash(requestHash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .createdAt(now)
                .lockedUntil(now.plus(LEASE))
                .expiresAt(now.plus(TTL))
                .build());
        return new Decision(Outcome.PROCEED, created, created.getLockedUntil());
    }

    private Decision evaluate(IdempotencyKeyModel existing, String requestHash, Instant now) {
        if (!existing.getRequestHash().equals(requestHash)) {
            return new Decision(Outcome.MISMATCH, existing);
        }
        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            return new Decision(Outcome.REPLAY, existing);
        }
        if (existing.getLockedUntil().isAfter(now)) {
            return new Decision(Outcome.IN_PROGRESS, existing);
        }
        // The previous attempt never finished (server restart, crash): take the lease over.
        Instant newLease = now.plus(LEASE);
        int updated = keys.takeOver(existing.getId(), existing.getLockedUntil(), newLease, IdempotencyStatus.IN_PROGRESS);
        if (updated == 0) {
            return new Decision(Outcome.IN_PROGRESS, existing);
        }
        existing.setLockedUntil(newLease);
        return new Decision(Outcome.PROCEED, existing, newLease);
    }

    /**
     * Stores a successful response inside the caller's (business) transaction.
     *
     * @return false if this request lost its lease; the caller must then roll back its changes.
     */
    public boolean completeInCurrentTransaction(UUID id, Instant lease, StoredResponse response) {
        return keys.completeIfOwned(id, lease, response.status(), response.body(),
                truncate(response.contentType(), 255), truncate(response.location(), 1024)) == 1;
    }

    /** Stores a response that changed nothing (e.g. a 404 or 409) in its own transaction. */
    public boolean complete(UUID id, Instant lease, StoredResponse response) {
        Boolean stored = tx.execute(s -> completeInCurrentTransaction(id, lease, response));
        return Boolean.TRUE.equals(stored);
    }

    /** Forgets a key this request still owns so the client can retry for real (e.g. after a 5xx). */
    public void release(UUID id, Instant lease) {
        tx.executeWithoutResult(s -> keys.releaseIfOwned(id, lease, IdempotencyStatus.IN_PROGRESS));
    }

    public int deleteExpired() {
        int[] deleted = {0};
        tx.executeWithoutResult(s -> deleted[0] = keys.deleteExpired(now()));
        return deleted[0];
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
