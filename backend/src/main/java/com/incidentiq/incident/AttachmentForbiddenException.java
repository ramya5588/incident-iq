package com.incidentiq.incident;

/** Signed in, but not allowed to add or remove this attachment (403). */
public class AttachmentForbiddenException extends RuntimeException {

    public AttachmentForbiddenException(String message) {
        super(message);
    }
}
