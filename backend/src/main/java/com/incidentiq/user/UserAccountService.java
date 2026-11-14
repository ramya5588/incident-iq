package com.incidentiq.user;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.incident.IncidentService;
import com.incidentiq.incident.UserNotFoundException;

@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final IncidentService incidentService;
    private final Clock clock;

    public UserAccountService(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            IncidentService incidentService,
            Clock clock) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.incidentService = incidentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return repository.findAll(Sort.by("displayName"))
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (repository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        UserRole role = request.role() == null
                ? UserRole.USER
                : request.role();

        UserAccount user = new UserAccount(
                normalizedEmail,
                request.displayName().trim(),
                role);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        return UserResponse.from(repository.save(user));
    }

    private UserAccount findUser(UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Changes a member's role; takes effect on their next request. The last
     * admin can't be demoted, so the team is never left without one.
     */
    @Transactional
    public UserResponse changeRole(UUID userId, UserRole role, UUID adminId) {
        UserAccount user = findUser(userId);
        UserRole previous = user.getRole();

        if (previous == role) {
            return UserResponse.from(user);
        }
        if (previous == UserRole.ADMIN && user.isActive()
                && repository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.setRole(role);
        UserAccount saved = repository.saveAndFlush(user);
        log.info("Admin {} changed the role of user {} from {} to {}",
                adminId, userId, previous, role);
        return UserResponse.from(saved);
    }

    /** Admin edit of name and/or email; the email stays unique (any case). */
    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request, UUID adminId) {
        UserAccount user = findUser(userId);

        if (request.email() != null) {
            String email = request.email().toLowerCase(Locale.ROOT);
            if (!email.equals(user.getEmail())) {
                repository.findByEmailIgnoreCase(email)
                        .filter(other -> !other.getId().equals(userId))
                        .ifPresent(other -> {
                            throw new DuplicateEmailException(email);
                        });
                user.setEmail(email);
            }
        }
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }

        UserAccount saved = repository.saveAndFlush(user);
        log.info("Admin {} updated the details of user {}", adminId, userId);
        return UserResponse.from(saved);
    }

    /**
     * Ends a member's access while keeping their history. They're signed out
     * on their next request, can't sign in, can't be assigned and get no
     * notifications. Optionally returns their open incidents to the queue.
     */
    @Transactional
    public DeactivationResponse deactivate(
            UUID userId, boolean unassignOpenIncidents, UUID adminId) {

        if (userId.equals(adminId)) {
            throw new TeamRuleException("You can't deactivate your own account.");
        }

        UserAccount user = findUser(userId);
        if (!user.isActive()) {
            return new DeactivationResponse(UserResponse.from(user), 0);
        }
        if (user.getRole() == UserRole.ADMIN
                && repository.countByRoleAndActiveTrue(UserRole.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.deactivate(clock.instant().truncatedTo(ChronoUnit.MICROS));
        repository.saveAndFlush(user);

        int unassigned = unassignOpenIncidents
                ? incidentService.unassignOpenIncidents(userId, adminId)
                : 0;

        log.info("Admin {} deactivated user {} (unassigned {} incident(s))",
                adminId, userId, unassigned);
        return new DeactivationResponse(UserResponse.from(user), unassigned);
    }

    @Transactional
    public UserResponse reactivate(UUID userId, UUID adminId) {
        UserAccount user = findUser(userId);
        if (!user.isActive()) {
            user.reactivate();
            repository.saveAndFlush(user);
            log.info("Admin {} reactivated user {}", adminId, userId);
        }
        return UserResponse.from(user);
    }

    /** Sets a new password; the member's existing sessions are signed out. */
    @Transactional
    public void resetPassword(UUID userId, String newPassword, UUID adminId) {
        UserAccount user = findUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        repository.saveAndFlush(user);
        log.info("Admin {} reset the password of user {}", adminId, userId);
    }
}