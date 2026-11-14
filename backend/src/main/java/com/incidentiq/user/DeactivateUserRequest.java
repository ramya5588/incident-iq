package com.incidentiq.user;

/**
 * @param unassignOpenIncidents also unassign their open and in-progress
 *        incidents, putting them back in the queue
 */
public record DeactivateUserRequest(boolean unassignOpenIncidents) {
}
