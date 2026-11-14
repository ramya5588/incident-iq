package com.incidentiq.auth;

/** A change to your own account that can't be made as asked (400). */
public class AccountRuleException extends RuntimeException {

    public AccountRuleException(String message) {
        super(message);
    }
}
