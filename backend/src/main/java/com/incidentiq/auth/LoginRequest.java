package com.incidentiq.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password) {

    /** Hide the password if this record is ever logged. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
