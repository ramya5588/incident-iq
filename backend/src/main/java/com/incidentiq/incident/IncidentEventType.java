package com.incidentiq.incident;

public enum IncidentEventType {
    CREATED,
    STATUS_CHANGED,
    ASSIGNEE_CHANGED,
    AI_TRIAGED,
    COMMENT,
    TITLE_CHANGED,
    DESCRIPTION_EDITED,
    PRIORITY_CHANGED,
    CATEGORY_CHANGED,
    ATTACHMENT_ADDED,
    ATTACHMENT_REMOVED,
    /** The reporter rated how it was handled: toValue is the score, message the comment. */
    SATISFACTION_RATED
}
