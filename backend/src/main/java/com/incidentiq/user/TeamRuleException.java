package com.incidentiq.user;

/**
 * A change that would break a team rule (e.g. leaving no admin, or
 * deactivating yourself). Answered with 409 Conflict and the message.
 */
public class TeamRuleException extends RuntimeException {

    public TeamRuleException(String message) {
        super(message);
    }
}
