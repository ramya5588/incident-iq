package com.incidentiq.incident;

/** A report asked for with an invalid range or time zone (400). */
public class InvalidReportException extends RuntimeException {

    public InvalidReportException(String message) {
        super(message);
    }
}
