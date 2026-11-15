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

    /** Still someone's work: counts toward workload, unassigned on deactivation. */
    static final List<IncidentStatus> ACTIVE_STATUSES = List.of(
            IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS, IncidentStatus.ON_HOLD);

    private final IncidentRepository incidentRepository;
    /** Longest comment, as people read it (mentions count as @Name). */
    static final int COMMENT_MAX_LENGTH = 2000;

    private final UserAccountRepository userRepository;
    private final EntityManager entityManager;
    private final IncidentTriageClient triageClient;
    private final IncidentEventRepository eventRepository;
    private final SlaProperties slaProperties;
    private final Clock clock;
    private final NotificationService notifications;
    private final BusinessHoursService businessHours;
    private final WatcherService watchers;

    public IncidentService(
            IncidentRepository incidentRepository,
            UserAccountRepository userRepository,
            EntityManager entityManager,
            IncidentTriageClient triageClient,
            IncidentEventRepository eventRepository,
            SlaProperties slaProperties,
            Clock clock,
            NotificationService notifications,
            BusinessHoursService businessHours,
            WatcherService watchers) {

        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.triageClient = triageClient;
        this.eventRepository = eventRepository;
        this.slaProperties = slaProperties;
        this.clock = clock;
        this.notifications = notifications;
        this.businessHours = businessHours;
        this.watchers = watchers;
    }

    /** Current time at the database's precision (microseconds). */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private IncidentResponse toResponse(Incident incident) {
        return toResponse(incident, businessHours.calendars());
    }

    /** For lists: load the calendars once, not per incident. */
    private IncidentResponse toResponse(Incident incident, SlaCalendars calendars) {
        SlaCalendar calendar = calendars.forPriority(incident.getPriority());
        return IncidentResponse.from(incident, IncidentSla.of(incident, now(), calendar));
    }

    private SlaCalendar calendarFor(Incident incident) {
        return businessHours.calendars().forPriority(incident.getPriority());
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
        incident.startSla(now(), slaProperties.targetFor(priority),
                businessHours.calendars().forPriority(priority));

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
        IncidentStatus next = request.status();

        if (next == IncidentStatus.ON_HOLD) {
            if (request.holdReason() == null) {
                throw new InvalidStatusChangeException(
                        "Choose why the incident is on hold.");
            }
            boolean changed = previous != next
                    || request.holdReason() != incident.getHoldReason()
                    || !Objects.equals(request.holdNote(), incident.getHoldNote());
            if (changed) {
                incident.putOnHold(request.holdReason(), request.holdNote(), now());
                eventRepository.save(IncidentEvent.statusChanged(
                        incident, previous, next, holdMessage(incident), actor));
                if (previous != next) {
                    notifications.notifyStatusChanged(incident, next, actor);
                }
            }
        } else if (previous != next) {
            incident.updateStatus(next, now(), calendarFor(incident));
            eventRepository.save(IncidentEvent.statusChanged(incident, previous, next, actor));
            notifications.notifyStatusChanged(incident, next, actor);
        }

        return toResponse(incidentRepository.saveAndFlush(incident));
    }

    /** "Waiting on reporter: asked for a screenshot" (shown on the timeline). */
    private static String holdMessage(Incident incident) {
        String label = incident.getHoldReason().label();
        return incident.getHoldNote() == null ? label : label + ": " + incident.getHoldNote();
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
                    request.priority(), slaProperties.targetFor(request.priority()),
                    businessHours.calendars().forPriority(request.priority()));
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
     * Unassigns every open, in-progress or on-hold incident of {@code userId} (e.g.
     * when they're deactivated), recording each change on the timeline.
     *
     * @return how many incidents were unassigned
     */
    @Transactional
    public int unassignOpenIncidents(UUID userId, UUID actorId) {
        UserAccount actor = findActor(actorId);
        List<Incident> incidents = incidentRepository.findByAssignedToIdAndStatusIn(
                userId, ACTIVE_STATUSES);

        for (Incident incident : incidents) {
            UserAccount previous = incident.getAssignedTo();
            incident.assignTo(null, now());
            eventRepository.save(IncidentEvent.assigneeChanged(incident, previous, null, actor));
        }
        incidentRepository.flush();
        return incidents.size();
    }

    /**
     * The reporter rates how a resolved or closed incident was handled. They
     * can change their rating later; each rating shows on the timeline.
     */
    @Transactional
    public IncidentResponse rate(UUID incidentId, RateIncidentRequest request, UUID actorId) {
        Incident incident = findIncident(incidentId);
        UserAccount actor = findActor(actorId);
        if (!incident.getCreatedBy().getId().equals(actor.getId())) {
            throw new RatingNotAllowedException();
        }
        if (incident.getStatus() != IncidentStatus.RESOLVED
                && incident.getStatus() != IncidentStatus.CLOSED) {
            throw new InvalidStatusChangeException("You can rate an incident once it's resolved.");
        }

        incident.rate(request.score(), request.comment(), now());
        eventRepository.save(IncidentEvent.rated(incident, request.score(), request.comment(), actor));
        return toResponse(incidentRepository.saveAndFlush(incident));
    }

    @Transactional(readOnly = true)
    public List<IncidentEventResponse> getEvents(UUID incidentId, boolean includeInternal) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new IncidentNotFoundException(incidentId);
        }

        List<IncidentEvent> events = includeInternal
                ? eventRepository.findAllByIncidentIdOrderByIdDesc(incidentId)
                : eventRepository.findAllByIncidentIdAndInternalFalseOrderByIdDesc(incidentId);
        return events
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
        boolean internal = request.isInternal();
        if (internal && author.getRole() == UserRole.USER) {
            throw new InternalNoteForbiddenException();
        }

        Mentions.Resolved mentions = Mentions.resolve(
                request.body(), userRepository::findById,
                user -> canBeMentioned(user, incident, internal));
        if (Mentions.visibleText(mentions.body()).length() > COMMENT_MAX_LENGTH) {
            throw new InvalidCommentException(
                    "Comment must not exceed " + COMMENT_MAX_LENGTH + " characters");
        }
        // Mentioned people follow the incident from now on, so they hear the replies.
        watchers.startWatching(incident.getId(), mentions.mentioned().stream()
                .map(UserAccount::getId)
                .filter(id -> !id.equals(author.getId()))
                .toList());

        if (internal) {
            // Only for the team: no change to the incident, and the
            // reporter is never told.
            IncidentEvent note = eventRepository.saveAndFlush(
                    IncidentEvent.internalNote(incident, author, mentions.body()));
            notifications.notifyMentioned(incident, author, mentions.mentioned(), true);
            notifications.notifyInternalNote(incident, author, mentions.mentioned());
            return IncidentEventResponse.from(note);
        }

        IncidentEvent comment = eventRepository.saveAndFlush(
                IncidentEvent.comment(incident, author, mentions.body()));

        // The reply they were waiting for: work resumes and the clock restarts.
        boolean resumed = incident.getStatus() == IncidentStatus.ON_HOLD
                && incident.getHoldReason() == HoldReason.WAITING_ON_REPORTER
                && incident.getCreatedBy().getId().equals(author.getId());
        if (resumed) {
            incident.updateStatus(IncidentStatus.IN_PROGRESS, now(), calendarFor(incident));
            eventRepository.save(IncidentEvent.statusChanged(
                    incident, IncidentStatus.ON_HOLD, IncidentStatus.IN_PROGRESS,
                    "Resumed when the reporter replied", author));
            incidentRepository.saveAndFlush(incident);
        }
        notifications.notifyMentioned(incident, author, mentions.mentioned(), false);
        notifications.notifyCommented(incident, author, resumed, mentions.mentioned());

        return IncidentEventResponse.from(comment);
    }

    /**
     * Agents and admins can always be mentioned. Reporters only in a public
     * comment on their own incident, so mentions never reach other reporters
     * and never reveal an internal note.
     */
    private static boolean canBeMentioned(UserAccount user, Incident incident, boolean internal) {
        if (!user.isActive()) {
            return false;
        }
        if (user.getRole() != UserRole.USER) {
            return true;
        }
        return !internal && incident.getCreatedBy().getId().equals(user.getId());
    }

    @Transactional(readOnly = true)
    public List<AssigneeWorkloadResponse> getWorkload() {
        return incidentRepository
                .countActiveByAssignee(ACTIVE_STATUSES)
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
                incidentRepository.countByStatus(IncidentStatus.ON_HOLD),
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

        return getIncidents(status, priority, search, overdue, null, pageable);
    }

    /** {@code watchedBy}: only incidents this person watches (null for all). */
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidents(
            IncidentStatus status,
            IncidentPriority priority,
            String search,
            boolean overdue,
            UUID watchedBy,
            Pageable pageable) {

        var filter = IncidentSpecifications.filter(status, priority, search);
        if (overdue) {
            filter = filter.and(IncidentSpecifications.overdue(now()));
        }
        if (watchedBy != null) {
            filter = filter.and(IncidentSpecifications.watchedBy(watchedBy));
        }

        SlaCalendars calendars = businessHours.calendars();
        return incidentRepository.findAll(filter, pageable)
                .map(incident -> toResponse(incident, calendars));
    }
}