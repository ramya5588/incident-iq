package com.incidentiq.user;

/** Refuses to leave the team without any admin (409 Conflict). */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("There must always be at least one admin. Make someone else an admin first.");
    }
}
