package com.incidentiq.auth;

/** Reset links need email, which isn't set up on this server (409). */
public class PasswordResetUnavailableException extends RuntimeException {

    public PasswordResetUnavailableException() {
        super("Password reset by email isn't available because email isn't set up on this server.");
    }
}
