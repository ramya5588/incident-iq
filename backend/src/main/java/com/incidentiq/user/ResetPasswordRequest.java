package com.incidentiq.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An admin sets a new password for a member (e.g. they're locked out). */
public record ResetPasswordRequest(

        // BCrypt only uses the first 72 bytes, so cap it there.
        @NotBlank(message = "New password is required") @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters") String newPassword) {

    /** Hide the password if this record is ever logged. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[***]";
    }
}
