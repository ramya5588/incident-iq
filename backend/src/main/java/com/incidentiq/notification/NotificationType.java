package com.incidentiq.notification;

public enum NotificationType {
    /** An incident was assigned to the recipient. */
    ASSIGNED,
    /** Someone commented on an incident the recipient reported or is assigned. */
    COMMENTED,
    MENTIONED,
    /** The status of an incident the recipient reported changed. */
    STATUS_CHANGED,
    /** A response or resolution deadline was missed. */
    SLA_BREACHED
}
