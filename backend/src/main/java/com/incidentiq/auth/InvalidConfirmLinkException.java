package com.incidentiq.auth;

/** Unknown, used, cancelled or expired email confirmation link (400). */
public class InvalidConfirmLinkException extends RuntimeException {

    public InvalidConfirmLinkException() {
        super("This confirmation link is invalid or has expired. Ask for the change again.");
    }
}
