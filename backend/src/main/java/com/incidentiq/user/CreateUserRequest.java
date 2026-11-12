package com.incidentiq.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,

        @NotBlank(message = "Display name is required") @Size(max = 120, message = "Display name must not exceed 120 characters") String displayName,

        UserRole role,

        // BCrypt only uses the first 72 bytes, so cap it there.
        @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {

    /** Hide the password if this record is ever logged. */
    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", displayName=" + displayName
                + ", role=" + role + "]";
    }

    /**
     * Trim input before bean validation runs, so an address with stray
     * surrounding spaces is cleaned up rather than rejected by {@code @Email}.
     */
    public CreateUserRequest {
        email = email == null ? null : email.trim();
        displayName = displayName == null ? null : displayName.trim();
    }
}
