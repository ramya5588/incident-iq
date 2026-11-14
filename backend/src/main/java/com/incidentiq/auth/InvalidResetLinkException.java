package com.incidentiq.auth;

/** Unknown, used, cancelled or expired reset link (400). */
public class InvalidResetLinkException extends RuntimeException {

    public InvalidResetLinkException() {
        super("This reset link is invalid or has expired. Ask for a new one.");
    }
}
