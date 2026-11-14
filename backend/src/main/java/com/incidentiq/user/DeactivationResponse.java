package com.incidentiq.user;

/** The deactivated member, and how many incidents were unassigned from them. */
public record DeactivationResponse(UserResponse user, int unassignedIncidents) {
}
