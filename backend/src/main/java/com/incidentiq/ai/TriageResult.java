package com.incidentiq.ai;

import com.incidentiq.incident.IncidentPriority;

public record TriageResult(
        String summary,
        String category,
        IncidentPriority suggestedPriority,
        String reasoning) {
}