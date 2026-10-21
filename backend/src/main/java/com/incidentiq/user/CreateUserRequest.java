package com.incidentiq.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,

        @NotBlank(message = "Display name is required") @Size(max = 120, message = "Display name must not exceed 120 characters") String displayName,

        UserRole role) {
}