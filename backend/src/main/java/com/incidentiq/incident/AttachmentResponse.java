package com.incidentiq.incident;

import java.time.Instant;
import java.util.UUID;

/** An attachment as listed on an incident (without its bytes). */
public record AttachmentResponse(
        UUID id,
        String fileName,
        String contentType,
        int sizeBytes,
        boolean image,
        UUID uploadedById,
        String uploadedByName,
        Instant createdAt) {

    static AttachmentResponse from(IncidentAttachmentRepository.Info info) {
        return new AttachmentResponse(
                info.getId(), info.getFileName(), info.getContentType(), info.getSizeBytes(),
                info.getContentType().startsWith("image/"),
                info.getUploadedById(), info.getUploadedByName(), info.getCreatedAt());
    }
}
