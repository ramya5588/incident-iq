package com.incidentiq.user;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserAccountRepository repository;

    public UserAccountService(UserAccountRepository repository) {
        this.repository = repository;
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

        return UserResponse.from(repository.save(user));
    }
}