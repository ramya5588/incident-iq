package com.incidentiq.ai;

public interface IncidentTriageClient {

    TriageResult triage(String title, String description);
}