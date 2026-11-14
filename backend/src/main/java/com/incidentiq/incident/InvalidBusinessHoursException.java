package com.incidentiq.incident;

/** Business-hours settings that can't be saved as given (400). */
public class InvalidBusinessHoursException extends RuntimeException {

    public InvalidBusinessHoursException(String message) {
        super(message);
    }
}
