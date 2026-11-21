package com.EOT.project.repository;

import com.EOT.project.model.IdempotencyKeyModel;
import com.EOT.project.model.IdempotencyStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyModel, UUID> {
    Optional<IdempotencyKeyModel> findByAuthSubAndIdemKey(String authSub, String idemKey);

    /**
     * Atomically extends a stale lease. Returns 0 if another request took it first or if the
     * request finished meanwhile (status no longer IN_PROGRESS).
     */
    @Transactional
    @Modifying
    @Query("""
            update IdempotencyKeyModel k set k.lockedUntil = :newLease
            where k.id = :id and k.status = :inProgress and k.lockedUntil = :previousLease""")
    int takeOver(@Param("id") UUID id, @Param("previousLease") Instant previousLease,
                 @Param("newLease") Instant newLease, @Param("inProgress") IdempotencyStatus inProgress);

    /**
     * Stores the response only if this request still holds the lease. Runs inside the caller's
     * transaction, so the business change and its stored response commit (or roll back) together.
     */
    @Transactional
    @Modifying
    @Query("""
            update IdempotencyKeyModel k
            set k.status = com.EOT.project.model.IdempotencyStatus.COMPLETED, k.responseStatus = :status,
                k.responseBody = :body, k.responseContentType = :contentType, k.locationHeader = :location
            where k.id = :id and k.status = com.EOT.project.model.IdempotencyStatus.IN_PROGRESS
                and k.lockedUntil = :lease""")
    int completeIfOwned(@Param("id") UUID id, @Param("lease") Instant lease, @Param("status") int status,
                        @Param("body") String body, @Param("contentType") String contentType,
                        @Param("location") String location);

    @Transactional
    @Modifying
    @Query("delete from IdempotencyKeyModel k where k.id = :id and k.status = :inProgress and k.lockedUntil = :lease")
    int releaseIfOwned(@Param("id") UUID id, @Param("lease") Instant lease,
                       @Param("inProgress") IdempotencyStatus inProgress);

    @Transactional
    @Modifying
    @Query("delete from IdempotencyKeyModel k where k.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
