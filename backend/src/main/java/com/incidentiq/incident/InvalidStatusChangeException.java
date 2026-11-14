package com.incidentiq.incident;

/** A status change that can't be made as asked (400). */
public class InvalidStatusChangeException extends RuntimeException {

    public InvalidStatusChangeException(String message) {
        super(message);
    }
}
