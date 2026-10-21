package com.incidentiq.user;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("A user already exists with email: " + email);
    }
}