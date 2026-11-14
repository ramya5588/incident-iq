package com.incidentiq.user;

import java.net.URI;
import java.util.List;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.auth.AppUserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAccountService service;

    public UserController(UserAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return service.getUsers();
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse response = service.createUser(request);

        URI location = URI.create("/api/users/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    /** Admin only (SecurityConfig). */
    @PatchMapping("/{userId}")
    public UserResponse updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal admin) {

        return service.updateUser(userId, request, admin.id());
    }

    /** Admin only (SecurityConfig). */
    @PostMapping("/{userId}/deactivate")
    public DeactivationResponse deactivate(
            @PathVariable UUID userId,
            @RequestBody(required = false) DeactivateUserRequest request,
            @AuthenticationPrincipal AppUserPrincipal admin) {

        boolean unassign = request != null && request.unassignOpenIncidents();
        return service.deactivate(userId, unassign, admin.id());
    }

    /** Admin only (SecurityConfig). */
    @PostMapping("/{userId}/reactivate")
    public UserResponse reactivate(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AppUserPrincipal admin) {

        return service.reactivate(userId, admin.id());
    }

    /** Admin only (SecurityConfig). */
    @PatchMapping("/{userId}/role")
    public UserResponse changeRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal AppUserPrincipal admin) {

        return service.changeRole(userId, request.role(), admin.id());
    }

    /** Admin only (SecurityConfig). */
    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal AppUserPrincipal admin) {

        service.resetPassword(userId, request.newPassword(), admin.id());
        return ResponseEntity.noContent().build();
    }
}