package com.incidentiq.reply;

import java.time.Instant;
import java.util.UUID;

/** {@code canEdit}: whether the person asking may change or delete it. */
public record SavedReplyResponse(
        UUID id, String title, String body, boolean shared, boolean canEdit, Instant updatedAt) {

    static SavedReplyResponse of(SavedReply reply, boolean canEdit) {
        return new SavedReplyResponse(
                reply.getId(), reply.getTitle(), reply.getBody(), reply.isShared(), canEdit,
                reply.getUpdatedAt());
    }
}
