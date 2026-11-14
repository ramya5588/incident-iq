package com.incidentiq.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin edit of a member's name and/or email. Omitted (null) fields are left
 * unchanged. Values are trimmed before validation.
 */
public record UpdateUserRequest(

        @Email(message = "Email must be valid")
        @Pattern(regexp = "(?s).*\\S.*", message = "Email must not be blank")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Pattern(regexp = "(?s).*\\S.*", message = "Display name must not be blank")
        @Size(max = 120, message = "Display name must not exceed 120 characters")
        String displayName) {

    public UpdateUserRequest {
        email = email == null ? null : email.trim();
        displayName = displayName == null ? null : displayName.trim();
    }
}
