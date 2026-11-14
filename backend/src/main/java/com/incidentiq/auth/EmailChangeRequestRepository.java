package com.incidentiq.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<EmailChangeRequest> findByTokenHash(String tokenHash);

    /** The user's open request, if any (there's at most one). */
    Optional<EmailChangeRequest> findFirstByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(
            UUID userId, Instant now);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /** Confirms one request, atomically: of two racing confirmations only one gets 1. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EmailChangeRequest r set r.usedAt = :now
            where r.tokenHash = :tokenHash and r.usedAt is null
            """)
    int use(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** Cancels the user's open requests. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EmailChangeRequest r set r.usedAt = :now
            where r.user.id = :userId and r.usedAt is null
            """)
    int cancelAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
