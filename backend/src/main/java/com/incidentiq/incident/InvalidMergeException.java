package com.incidentiq.incident;

/** A duplicate merge that can't be made as asked (400). */
public class InvalidMergeException extends RuntimeException {

    public InvalidMergeException(String message) {
        super(message);
    }
}
