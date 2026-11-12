package com.incidentiq.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "Role is required") UserRole role) {
}
