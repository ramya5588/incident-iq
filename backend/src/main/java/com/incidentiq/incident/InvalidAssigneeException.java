package com.incidentiq.incident;

/** Only agents and admins can be assigned incidents (400 Bad Request). */
public class InvalidAssigneeException extends RuntimeException {

    public InvalidAssigneeException(String displayName) {
        super(displayName + " can't be assigned incidents: only agents and admins can.");
    }
}
