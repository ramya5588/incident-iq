package com.incidentiq.notification;

import com.incidentiq.user.UserAccount;

import jakarta.validation.constraints.NotNull;

/**
 * Which notifications are also emailed. The first four are required when
 * saving; {@code mentioned} is left as it is when omitted.
 */
public record EmailPreferences(
        @NotNull Boolean assigned,
        @NotNull Boolean commented,
        @NotNull Boolean statusChanged,
        @NotNull Boolean slaBreached,
        Boolean mentioned) {

    static EmailPreferences of(UserAccount user) {
        return new EmailPreferences(
                user.isEmailOnAssigned(),
                user.isEmailOnCommented(),
                user.isEmailOnStatusChanged(),
                user.isEmailOnSlaBreached(),
                user.isEmailOnMentioned());
    }

    /**
     * @param emailEnabled false when the server has no mail set up, so
     *        nothing is emailed whatever is chosen
     */
    public record Response(
            boolean assigned,
            boolean commented,
            boolean statusChanged,
            boolean slaBreached,
            boolean mentioned,
            boolean emailEnabled) {

        static Response of(UserAccount user, boolean emailEnabled) {
            return new Response(
                    user.isEmailOnAssigned(),
                    user.isEmailOnCommented(),
                    user.isEmailOnStatusChanged(),
                    user.isEmailOnSlaBreached(),
                    user.isEmailOnMentioned(),
                    emailEnabled);
        }
    }
}
