package com.incidentiq.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class OpenAiTriageOutput {

    @JsonPropertyDescription("A concise incident summary containing no more than two sentences.")
    public String summary;

    @JsonPropertyDescription("The incident category, such as Authentication, Security, Availability, Performance, Database, or General.")
    public String category;

    @JsonPropertyDescription("The recommended priority. Must be LOW, MEDIUM, HIGH, or CRITICAL.")
    public String suggestedPriority;

    @JsonPropertyDescription("A brief explanation for the category and priority selections.")
    public String reasoning;
}