package com.incidentiq.auth;

/** Asked for too many emailed links in a short time (429). */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
