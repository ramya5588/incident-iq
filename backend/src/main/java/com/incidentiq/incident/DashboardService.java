package com.incidentiq.incident;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.notification.Notification;
import com.incidentiq.notification.NotificationRepository;
import com.incidentiq.notification.NotificationType;

/** The personal part of the dashboard: what needs doing now. */
@Service
public class DashboardService {

    static final int LIST_LIMIT = 10;
    static final int QUEUE_LIMIT = 5;
    static final int UNREAD_LIMIT = 5;

    private static final List<IncidentStatus> ACTIVE =
            List.of(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS, IncidentStatus.ON_HOLD);

    private final IncidentRepository incidents;
    private final NotificationRepository notifications;
    private final IncidentService incidentService;

    public DashboardService(
            IncidentRepository incidents,
            NotificationRepository notifications,
            IncidentService incidentService) {

        this.incidents = incidents;
        this.notifications = notifications;
        this.incidentService = incidentService;
    }

    /**
     * For agents and admins: your open and in-progress incidents by the next
     * deadline, your incidents on hold (longest waiting first), the oldest
     * unassigned ones, and incidents with unread notifications for you.
     */
    @Transactional(readOnly = true)
    public MyWorkResponse myWork(UUID userId) {
        List<Incident> due = incidents.findAssignedByNextDeadline(
                userId, List.of(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS), PageRequest.of(0, LIST_LIMIT));
        List<Incident> waiting = incidents.findByAssignedToIdAndStatusOrderByOnHoldSinceAsc(
                userId, IncidentStatus.ON_HOLD, PageRequest.of(0, LIST_LIMIT));
        List<Incident> unassigned = incidents.findUnassigned(ACTIVE, PageRequest.of(0, QUEUE_LIMIT));

        return new MyWorkResponse(
                incidentService.responses(due),
                incidentService.responses(waiting),
                incidentService.responses(unassigned),
                incidents.countUnassigned(ACTIVE),
                unread(userId));
    }

    /** For everyone: the incidents you reported, most recently changed first. */
    @Transactional(readOnly = true)
    public List<IncidentResponse> myIncidents(UUID userId) {
        return incidentService.responses(incidents.findByCreatedByIdAndDuplicateOfIsNullOrderByUpdatedAtDesc(
                userId, PageRequest.of(0, LIST_LIMIT)));
    }

    /** The latest unread notification for each of up to five incidents. */
    private List<MyWorkResponse.Unread> unread(UUID userId) {
        Set<UUID> seen = new LinkedHashSet<>();
        List<MyWorkResponse.Unread> result = new ArrayList<>();
        for (Notification notification : notifications.findByRecipientIdAndReadAtIsNullOrderByIdDesc(
                userId, PageRequest.of(0, 50))) {
            Incident incident = notification.getIncident();
            if (result.size() < UNREAD_LIMIT && seen.add(incident.getId())) {
                result.add(new MyWorkResponse.Unread(
                        incident.getId(), incident.getIncidentNumber(), incident.getTitle(),
                        notification.getType(), notification.getMessage(), notification.getCreatedAt()));
            }
        }
        return result;
    }

    public record MyWorkResponse(
            List<IncidentResponse> dueSoon,
            List<IncidentResponse> waiting,
            List<IncidentResponse> unassigned,
            long unassignedTotal,
            List<Unread> unread) {

        public record Unread(
                UUID incidentId, Long incidentNumber, String title,
                NotificationType type, String message, Instant createdAt) {
        }
    }
}
