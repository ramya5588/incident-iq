package com.incidentiq.incident;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(

        @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must not exceed 200 characters") String title,

        @NotBlank(message = "Description is required") @Size(max = 5000, message = "Description must not exceed 5000 characters") String description,

        IncidentPriority priority,

        @Size(max = 100, message = "Category must not exceed 100 characters") String category,

        @NotNull(message = "Created-by user ID is required") UUID createdById) {
}