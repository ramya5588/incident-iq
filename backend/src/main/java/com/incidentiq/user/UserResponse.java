package com.incidentiq.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        boolean active,
        Instant deactivatedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.isActive(),
                user.getDeactivatedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}