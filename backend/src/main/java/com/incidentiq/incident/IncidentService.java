package com.incidentiq.incident;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;

import jakarta.persistence.EntityManager;

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

        // Reload database-generated values such as incident_number.
        entityManager.refresh(savedIncident);

        return IncidentResponse.from(savedIncident);
    }
}