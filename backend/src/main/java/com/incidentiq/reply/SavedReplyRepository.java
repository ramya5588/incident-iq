package com.incidentiq.reply;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SavedReplyRepository extends JpaRepository<SavedReply, UUID> {

    /** Shared replies, then this person's own, each by title. */
    @Query("""
            select r from SavedReply r
             where r.ownerId is null or r.ownerId = :userId
             order by case when r.ownerId is null then 0 else 1 end, lower(r.title)""")
    List<SavedReply> findVisibleTo(UUID userId);
}
