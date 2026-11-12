package com.incidentiq.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Current password is required") String currentPassword,

        // BCrypt only uses the first 72 bytes, so cap it there.
        @NotBlank(message = "New password is required") @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters") String newPassword) {

    /** Hide the passwords if this record is ever logged. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
