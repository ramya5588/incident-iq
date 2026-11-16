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
    SATISFACTION_RATED,
    /** On the duplicate: toValue is the main incident's number. */
    MERGED_INTO,
    /** On the main incident: toValue is the duplicate's number. */
    DUPLICATE_MERGED,
    /** On the former duplicate: toValue is the former main incident's number. */
    UNMERGED,
    /** On the main incident: toValue is the former duplicate's number. */
    DUPLICATE_UNMERGED
}
