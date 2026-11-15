package com.incidentiq.incident;

/** A reporter tried to add an internal note (403). */
public class InternalNoteForbiddenException extends RuntimeException {

    public InternalNoteForbiddenException() {
        super("Only agents and admins can add internal notes.");
    }
}
