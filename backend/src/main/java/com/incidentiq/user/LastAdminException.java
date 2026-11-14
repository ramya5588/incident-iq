package com.incidentiq.user;

/** Refuses to leave the team without any active admin (409 Conflict). */
public class LastAdminException extends TeamRuleException {

    public LastAdminException() {
        super("There must always be at least one active admin. Make someone else an admin first.");
    }
}
