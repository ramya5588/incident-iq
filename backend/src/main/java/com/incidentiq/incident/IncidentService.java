package com.incidentiq.incident;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.incidentiq.ai.IncidentTriageClient;
import com.incidentiq.ai.TriageResult;
import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

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

    public IncidentService(
            IncidentRepository incidentRepository,
            UserAccountRepository userRepository,
            EntityManager entityManager,
            IncidentTriageClient triageClient,
            IncidentEventRepository eventRepository) {

        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.triageClient = triageClient;
        this.eventRepository = eventRepository;
    }

    private Incident findIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private UserAccount findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        UserAccount creator = userRepository.findById(request.createdById())
                .orElseThrow(() -> new UserNotFoundException(request.createdById()));

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

        Incident savedIncident = incidentRepository.saveAndFlush(incident);
        eventRepository.save(IncidentEvent.created(savedIncident, creator));

        entityManager.refresh(savedIncident);

        return IncidentResponse.from(savedIncident);
    }

    @Transactional
    public IncidentResponse triageIncident(UUID incidentId) {
        Incident incident = findIncident(incidentId);

        TriageResult result = triageClient.triage(
                incident.getTitle(),
                incident.getDescription());

        incident.applyAiTriage(result);

        Incident savedIncident = incidentRepository.saveAndFlush(incident);
        eventRepository.save(IncidentEvent.aiTriaged(
                savedIncident, savedIncident.getAiSummary()));

        return IncidentResponse.from(savedIncident);
    }

    @Transactional
    public IncidentResponse updateStatus(
            UUID incidentId,
            UpdateIncidentStatusRequest request) {

        Incident incident = findIncident(incidentId);
        IncidentStatus previous = incident.getStatus();

        incident.updateStatus(request.status());

        if (previous != request.status()) {
            eventRepository.save(IncidentEvent.statusChanged(
                    incident, previous, request.status()));
        }

        return IncidentResponse.from(
                incidentRepository.saveAndFlush(incident));
    }

    @Transactional
    public IncidentResponse updateAssignee(
            UUID incidentId,
            UpdateIncidentAssigneeRequest request) {

        Incident incident = findIncident(incidentId);
        UserAccount previous = incident.getAssignedTo();

        UserAccount assignee = request.assigneeId() == null
                ? null
                : findUser(request.assigneeId());

        incident.assignTo(assignee);

        UUID previousId = previous == null ? null : previous.getId();
        if (!Objects.equals(previousId, request.assigneeId())) {
            eventRepository.save(IncidentEvent.assigneeChanged(
                    incident, previous, assignee));
        }

        return IncidentResponse.from(
                incidentRepository.saveAndFlush(incident));
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
            CreateCommentRequest request) {

        Incident incident = findIncident(incidentId);
        UserAccount author = findUser(request.authorId());

        IncidentEvent comment = eventRepository.saveAndFlush(
                IncidentEvent.comment(incident, author, request.body()));

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
                : LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

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
                aiTriagedPercentage);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        return IncidentResponse.from(incident);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidents(
            IncidentStatus status,
            IncidentPriority priority,
            Pageable pageable) {

        Page<Incident> incidents;

        if (status != null && priority != null) {
            incidents = incidentRepository.findAllByStatusAndPriority(
                    status, priority, pageable);
        } else if (status != null) {
            incidents = incidentRepository.findAllByStatus(status, pageable);
        } else if (priority != null) {
            incidents = incidentRepository.findAllByPriority(priority, pageable);
        } else {
            incidents = incidentRepository.findAll(pageable);
        }

        return incidents.map(IncidentResponse::from);
    }
}