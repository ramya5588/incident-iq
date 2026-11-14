package com.incidentiq.notification;

import com.incidentiq.user.UserAccount;

import jakarta.validation.constraints.NotNull;

/** Which notifications are also emailed. All four are required when saving. */
public record EmailPreferences(
        @NotNull Boolean assigned,
        @NotNull Boolean commented,
        @NotNull Boolean statusChanged,
        @NotNull Boolean slaBreached) {

    static EmailPreferences of(UserAccount user) {
        return new EmailPreferences(
                user.isEmailOnAssigned(),
                user.isEmailOnCommented(),
                user.isEmailOnStatusChanged(),
                user.isEmailOnSlaBreached());
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
            boolean emailEnabled) {

        static Response of(UserAccount user, boolean emailEnabled) {
            return new Response(
                    user.isEmailOnAssigned(),
                    user.isEmailOnCommented(),
                    user.isEmailOnStatusChanged(),
                    user.isEmailOnSlaBreached(),
                    emailEnabled);
        }
    }
}
