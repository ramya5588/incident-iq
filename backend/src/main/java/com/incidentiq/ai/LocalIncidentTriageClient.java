package com.incidentiq.ai;

import java.util.Locale;

import com.incidentiq.incident.IncidentPriority;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "incidentiq.ai.provider", havingValue = "local", matchIfMissing = true)
public class LocalIncidentTriageClient implements IncidentTriageClient {

    @Override
    public TriageResult triage(String title, String description) {
        String combinedText = (title + " " + description)
                .toLowerCase(Locale.ROOT);

        String category = "General";
        IncidentPriority priority = IncidentPriority.MEDIUM;
        String reasoning = "No high-risk keywords were detected.";

        if (containsAny(
                combinedText,
                "security",
                "breach",
                "compromised",
                "unauthorized")) {

            category = "Security";
            priority = IncidentPriority.CRITICAL;
            reasoning = "Security-related language requires immediate review.";

        } else if (containsAny(
                combinedText,
                "outage",
                "unavailable",
                "service down",
                "production down")) {

            category = "Availability";
            priority = IncidentPriority.HIGH;
            reasoning = "The incident may affect service availability.";

        } else if (containsAny(
                combinedText,
                "login",
                "sign in",
                "authentication",
                "password")) {

            category = "Authentication";
            priority = IncidentPriority.HIGH;
            reasoning = "The incident appears to block user authentication.";

        } else if (containsAny(
                combinedText,
                "slow",
                "latency",
                "performance",
                "timeout")) {

            category = "Performance";
            priority = IncidentPriority.MEDIUM;
            reasoning = "The incident contains performance-related symptoms.";
        }

        return new TriageResult(
                createSummary(title, description),
                category,
                priority,
                reasoning);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String createSummary(String title, String description) {
        String normalizedDescription = description
                .trim()
                .replaceAll("\\s+", " ");

        String summary = title.trim() + " — " + normalizedDescription;

        if (summary.length() <= 240) {
            return summary;
        }

        return summary.substring(0, 237) + "...";
    }
}