package com.incidentiq.incident;

/** Why an incident is on hold. */
public enum HoldReason {
    /** Waiting for the reporter to reply; their next comment resumes work. */
    WAITING_ON_REPORTER("Waiting on reporter"),
    /** Waiting on a vendor, supplier or another team. */
    WAITING_ON_THIRD_PARTY("Waiting on third party"),
    /** Work is planned for a later time (e.g. a maintenance window). */
    SCHEDULED("Scheduled for later"),
    OTHER("Other");

    private final String label;

    HoldReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
