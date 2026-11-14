package com.incidentiq.incident;

/** Only active agents and admins can be assigned incidents (400 Bad Request). */
public class InvalidAssigneeException extends RuntimeException {

    private InvalidAssigneeException(String message) {
        super(message);
    }

    public static InvalidAssigneeException notAgentOrAdmin(String displayName) {
        return new InvalidAssigneeException(
                displayName + " can't be assigned incidents: only agents and admins can.");
    }

    public static InvalidAssigneeException deactivated(String displayName) {
        return new InvalidAssigneeException(
                displayName + " is deactivated and can't be assigned incidents.");
    }
}
