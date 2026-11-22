package com.EOT.project.repository;

import com.EOT.project.model.PasswordResetTokenModel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenModel, UUID> {

    Optional<PasswordResetTokenModel> findByTokenHash(String tokenHash);

    /** Newest request of a user, used for the resend cooldown. */
    Optional<PasswordResetTokenModel> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /** Marks the token as used only if nobody did before; returns 1 when this call won. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetTokenModel t set t.usedAt = :now where t.tokenId = :id and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** Invalidates the pending tokens of a user (a new request replaces the old link). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetTokenModel t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidatePending(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasswordResetTokenModel t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
