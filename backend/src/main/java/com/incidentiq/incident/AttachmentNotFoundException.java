package com.incidentiq.incident;

import java.util.UUID;

public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException(UUID attachmentId) {
        super("Attachment not found: " + attachmentId);
    }
}
