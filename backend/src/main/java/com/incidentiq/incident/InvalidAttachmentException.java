package com.incidentiq.incident;

/** A file that can't be attached as it is (400). */
public class InvalidAttachmentException extends RuntimeException {

    public InvalidAttachmentException(String message) {
        super(message);
    }
}
