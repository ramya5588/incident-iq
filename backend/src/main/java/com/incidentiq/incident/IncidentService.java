package com.incidentiq.incident;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.incidentiq.ai.IncidentTriageClient;
import com.incidentiq.ai.TriageResult;
import com.incidentiq.notification.NotificationService;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final UserAccountRepository userRepository;
    private final EntityManager entityManager;
    private final IncidentTriageClient triageClient;
    private final IncidentEventRepository eventRepository;
    private final SlaProperties slaProperties;
    private final Clock clock;
    private final NotificationService notifications;

    public IncidentService(
            IncidentRepository incidentRepository,
            UserAccountRepository userRepository,
            EntityManager entityManager,
            IncidentTriageClient triageClient,
            IncidentEventRepository eventRepository,
            SlaProperties slaProperties,
            Clock clock,
            NotificationService notifications) {

        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.triageClient = triageClient;
        this.eventRepository = eventRepository;
        this.slaProperties = slaProperties;
        this.clock = clock;
        this.notifications = notifications;
    }

    /** Current time at the database's precision (microseconds). */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private IncidentResponse toResponse(Incident incident) {
        return IncidentResponse.from(incident, IncidentSla.of(incident, now()));
    }

    private Incident findIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private UserAccount findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /** The signed-in user making the change (id comes from the session). */
    private UserAccount findActor(UUID actorId) {
        return findUser(actorId);
    }

    @Transactional
    public IncidentResponse createIncident(
            CreateIncidentRequest request,
            UUID reporterId) {

        UserAccount creator = findUser(reporterId);

        IncidentPriority priority = request.priority() == null
                ? IncidentPriority.MEDIUM
                : request.priority();

        String category = request.category() == null
                || request.category().isBlank()
                        ? null
                        : request.category().trim();

        Incident incident = new Incident(
                request.title().trim(),
                request.description().trim(),
                priority,
                category,
                creator);
        incident.startSla(now(), slaProperties.targetFor(priority));

        Incident savedIncident = incidentRepository.saveAndFlush(incident);
        eventRepository.save(IncidentEvent.created(savedIncident, creator));

        entityManager.refresh(savedIncident);

        return toResponse(savedIncident);
    }

    @Transactional
    public IncidentResponse triageIncident(UUID incidentId, UUID actorId) {
        Incident incident = findIncident(incidentId);
        UserAccount actor = findActor(actorId);

        TriageResult result = triageClient.triage(
                incident.getTitle(),
                incident.getDescription());

        incident.applyAiTriage(result, now());

        Incident savedIncident = incidentRepository.saveAndFlush(incident);
        eventRepository.save(IncidentEvent.aiTriaged(
                savedIncident, savedIncident.getAiSummary(), actor));

        return toResponse(savedIncident);
    }

    @Transactional
    public IncidentResponse updateStatus(
            UUID incidentId,
            UpdateIncidentStatusRequest request,
            UUID actorId) {

        Incident incident = findIncident(incidentId);
        UserAccount actor = findActor(actorId);
        IncidentStatus previous = incident.getStatus();

        if (previous != request.status()) {
            incident.updateStatus(request.status(), now());
            eventRepository.save(IncidentEvent.statusChanged(
                    incident, previous, request.status(), actor));
            notifications.notifyStatusChanged(incident, request.status(), actor);
        }

        return toResponse(incidentRepository.saveAndFlush(incident));
    }

    /**
     * Applies a partial edit and records one timeline entry per field that
     * actually changed. Unchanged or omitted fields are left alone.
     */
    @Transactional
    public IncidentResponse updateIncident(
            UUID incidentId,
            UpdateIncidentRequest request,
            UUID actorId) {

        Incident incident = findIncident(incidentId);
        UserAccount actor = findActor(actorId);

        if (request.title() != null
                && !request.title().equals(incident.getTitle())) {
            eventRepository.save(IncidentEvent.fieldChanged(
                    incident, IncidentEventType.TITLE_CHANGED,
                    incident.getTitle(), request.title(), actor));
            incident.rename(request.title());
        }

        if (request.description() != null
                && !request.description().equals(incident.getDescription())) {
            eventRepository.save(IncidentEvent.descriptionEdited(incident, actor));
            incident.describe(request.description());
        }

        if (request.priority() != null
                && request.priority() != incident.getPriority()) {
            eventRepository.save(IncidentEvent.fieldChanged(
                    incident, IncidentEventType.PRIORITY_CHANGED,
                    incident.getPriority().name(), request.priority().name(),
                    actor));
            incident.changePriority(
                    request.priority(), slaProperties.targetFor(request.priority()));
        }

        if (request.category() != null) {
            String category = request.category().isEmpty()
                    ? null
                    : request.category();

            if (!Objects.equals(category, incident.getCategory())) {
                eventRepository.save(IncidentEvent.fieldChanged(
                        incident, IncidentEventType.CATEGORY_CHANGED,
                        incident.getCategory(), category, actor));
                incident.changeCategory(category);
            }
        }

        return toResponse(incidentRepository.saveAndFlush(incident));
    }

    @Transactional
    public IncidentResponse updateAssignee(
            UUID incidentId,
            UpdateIncidentAssigneeRequest request,
            UUID actorId) {

        Incident incident = findIncident(incidentId);
        UserAccount actor = findActor(actorId);
        UserAccount previous = incident.getAssignedTo();

        UserAccount assignee = request.assigneeId() == null
                ? null
                : findUser(request.assigneeId());

        if (assignee != null && assignee.getRole() == UserRole.USER) {
            throw InvalidAssigneeException.notAgentOrAdmin(assignee.getDisplayName());
        }
        if (assignee != null && !assignee.isActive()) {
            throw InvalidAssigneeException.deactivated(assignee.getDisplayName());
        }

        incident.assignTo(assignee, now());

        UUID previousId = previous == null ? null : previous.getId();
        if (!Objects.equals(previousId, request.assigneeId())) {
            eventRepository.save(IncidentEvent.assigneeChanged(
                    incident, previous, assignee, actor));
            if (assignee != null) {
                notifications.notifyAssigned(incident, assignee, actor);
            }
        }

        return toResponse(incidentRepository.saveAndFlush(incident));
    }

    /**
     * Unassigns every open or in-progress incident of {@code userId} (e.g.
     * when they're deactivated), recording each change on the timeline.
     *
     * @return how many incidents were unassigned
     */
    @Transactional
    public int unassignOpenIncidents(UUID userId, UUID actorId) {
        UserAccount actor = findActor(actorId);
        List<Incident> incidents = incidentRepository.findByAssignedToIdAndStatusIn(
                userId, List.of(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS));

        for (Incident incident : incidents) {
            UserAccount previous = incident.getAssignedTo();
            incident.assignTo(null, now());
            eventRepository.save(IncidentEvent.assigneeChanged(incident, previous, null, actor));
        }
        incidentRepository.flush();
        return incidents.size();
    }

    @Transactional(readOnly = true)
    public List<IncidentEventResponse> getEvents(UUID incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new IncidentNotFoundException(incidentId);
        }

        return eventRepository.findAllByIncidentIdOrderByIdDesc(incidentId)
                .stream()
                .map(IncidentEventResponse::from)
                .toList();
    }

    @Transactional
    public IncidentEventResponse addComment(
            UUID incidentId,
            CreateCommentRequest request,
            UUID authorId) {

        Incident incident = findIncident(incidentId);
        UserAccount author = findUser(authorId);

        IncidentEvent comment = eventRepository.saveAndFlush(
                IncidentEvent.comment(incident, author, request.body()));
        notifications.notifyCommented(incident, author);

        return IncidentEventResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public List<AssigneeWorkloadResponse> getWorkload() {
        return incidentRepository
                .countActiveByAssignee(List.of(
                        IncidentStatus.OPEN,
                        IncidentStatus.IN_PROGRESS))
                .stream()
                .map(row -> new AssigneeWorkloadResponse(
                        row.getUserId(),
                        row.getActiveCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentStatsResponse getStats(Instant resolvedSince) {
        Instant since = resolvedSince != null
                ? resolvedSince
                : LocalDate.now(clock.withZone(ZoneOffset.UTC))
                        .atStartOfDay(ZoneOffset.UTC).toInstant();

        long total = incidentRepository.count();
        long aiTriaged = incidentRepository.countByAiTriagedAtIsNotNull();
        int aiTriagedPercentage = total == 0
                ? 0
                : (int) Math.round(aiTriaged * 100.0 / total);

        return new IncidentStatsResponse(
                total,
                incidentRepository.countByStatus(IncidentStatus.OPEN),
                incidentRepository.countByStatus(IncidentStatus.IN_PROGRESS),
                incidentRepository.countByStatus(IncidentStatus.RESOLVED),
                incidentRepository.countByStatus(IncidentStatus.CLOSED),
                since,
                incidentRepository.countByResolvedAtGreaterThanEqual(since),
                aiTriaged,
                aiTriagedPercentage,
                incidentRepository.count(IncidentSpecifications.overdue(now())));
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        return toResponse(incident);
    }

    /**
     * Lists incidents matching every filter given. {@code search} matches
     * title, description and category (case-insensitive, as plain text) or
     * the incident number ("42" or "INC-042"). {@code overdue} keeps only
     * open or in-progress incidents past a response or resolution deadline.
     */
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidents(
            IncidentStatus status,
            IncidentPriority priority,
            String search,
            boolean overdue,
            Pageable pageable) {

        var filter = IncidentSpecifications.filter(status, priority, search);
        if (overdue) {
            filter = filter.and(IncidentSpecifications.overdue(now()));
        }

        return incidentRepository.findAll(filter, pageable).map(this::toResponse);
    }
}