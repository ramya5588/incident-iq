package com.incidentiq.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /**
     * Uses up one link, atomically: of two requests racing with the same
     * link, only one gets 1 back.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.tokenHash = :tokenHash and t.usedAt is null
            """)
    int use(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /**
     * Cancels every unused link of the user. Pending changes are saved first,
     * and cached tokens are cleared so nothing reads a stale "unused" link.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.user.id = :userId and t.usedAt is null
            """)
    int cancelAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
