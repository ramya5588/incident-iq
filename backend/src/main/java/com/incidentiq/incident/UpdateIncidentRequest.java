package com.incidentiq.incident;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial edit of an incident's details. A {@code null} field means "leave
 * unchanged"; a blank category clears it. Title and description may be
 * omitted but not blank. Values are trimmed before validation.
 */
public record UpdateIncidentRequest(

        @Pattern(regexp = "(?s).*\\S.*", message = "Title must not be blank")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Pattern(regexp = "(?s).*\\S.*", message = "Description must not be blank")
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        IncidentPriority priority,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category) {

    public UpdateIncidentRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
        category = category == null ? null : category.trim();
    }
}
