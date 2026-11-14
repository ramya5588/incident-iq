package com.incidentiq.notification;

/**
 * An email about someone's account (e.g. a password reset link), sent
 * regardless of notification preferences, after the transaction commits.
 */
public record AccountEmail(String to, String subject, String text) {

    /** The text may hold a reset link: keep it out of logs. */
    @Override
    public String toString() {
        return "AccountEmail[subject=" + subject + "]";
    }
}
