package com.incidentiq.incident;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, UUID> {

    /** Everything about an attachment except its bytes. */
    interface Info {
        UUID getId();

        UUID getIncidentId();

        String getFileName();

        String getContentType();

        int getSizeBytes();

        UUID getUploadedById();

        String getUploadedByName();

        Instant getCreatedAt();
    }

    String INFO = """
            select a.id as id, a.incident.id as incidentId, a.fileName as fileName,
                   a.contentType as contentType, a.sizeBytes as sizeBytes,
                   a.uploadedBy.id as uploadedById, a.uploadedBy.displayName as uploadedByName,
                   a.createdAt as createdAt
            from IncidentAttachment a
            """;

    @Query(INFO + " where a.incident.id = :incidentId order by a.createdAt, a.id")
    List<Info> findInfoByIncidentId(@Param("incidentId") UUID incidentId);

    @Query(INFO + " where a.id = :id and a.incident.id = :incidentId")
    Optional<Info> findInfo(@Param("incidentId") UUID incidentId, @Param("id") UUID id);

    Optional<IncidentAttachment> findByIdAndIncidentId(UUID id, UUID incidentId);

    long countByIncidentId(UUID incidentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from IncidentAttachment a where a.id = :id")
    int deleteByIdQuietly(@Param("id") UUID id);
}
