package com.incidentiq.incident;

import java.util.UUID;

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

    public IncidentService(
            IncidentRepository incidentRepository,
            UserAccountRepository userRepository,
            EntityManager entityManager) {

        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
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

        entityManager.refresh(savedIncident);

        return IncidentResponse.from(savedIncident);
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

        if (status != null) {
            incidents = incidentRepository.findAllByStatus(status, pageable);
        } else if (priority != null) {
            incidents = incidentRepository.findAllByPriority(priority, pageable);
        } else {
            incidents = incidentRepository.findAll(pageable);
        }

        return incidents.map(IncidentResponse::from);
    }
}