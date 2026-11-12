package com.incidentiq.user;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.incident.UserNotFoundException;

@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
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
        if (previous == UserRole.ADMIN && repository.countByRole(UserRole.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.setRole(role);
        UserAccount saved = repository.saveAndFlush(user);
        log.info("Admin {} changed the role of user {} from {} to {}",
                adminId, userId, previous, role);
        return UserResponse.from(saved);
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