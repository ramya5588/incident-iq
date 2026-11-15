package com.incidentiq.incident;

/** A comment that can't be saved as it is (400). */
public class InvalidCommentException extends RuntimeException {

    public InvalidCommentException(String message) {
        super(message);
    }
}
