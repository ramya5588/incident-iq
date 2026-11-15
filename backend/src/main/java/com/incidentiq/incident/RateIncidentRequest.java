package com.incidentiq.incident;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 1 (poor) to 5 (great), with an optional comment. */
public record RateIncidentRequest(
        @NotNull(message = "Choose a rating from 1 to 5")
        @Min(value = 1, message = "Choose a rating from 1 to 5")
        @Max(value = 5, message = "Choose a rating from 1 to 5")
        Integer score,
        @Size(max = 1000, message = "Comment must be at most 1000 characters")
        String comment) {

    public RateIncidentRequest {
        comment = comment == null || comment.isBlank() ? null : comment.trim();
    }
}
