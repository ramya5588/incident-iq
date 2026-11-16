package com.incidentiq.incident;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The main incident this one duplicates. */
public record MarkDuplicateRequest(@NotNull(message = "Choose the main incident") UUID mainIncidentId) {
}
