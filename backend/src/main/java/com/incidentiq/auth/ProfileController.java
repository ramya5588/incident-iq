package com.incidentiq.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.user.UserResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The signed-in user's own name and sign-in email. Roles stay admin-only. */
@RestController
@RequestMapping("/api/auth")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @PatchMapping("/profile")
    public UserResponse updateProfile(
            @AuthenticationPrincipal AppUserPrincipal me,
            @Valid @RequestBody UpdateProfileRequest request) {

        return service.rename(me.id(), request.displayName());
    }

    /**
     * With email set up: starts a change confirmed from the new address
     * ({@code pending} set). Without: changes it now ({@code pending} null).
     */
    @PostMapping("/email-change")
    public ProfileService.EmailChangeResult requestEmailChange(
            @AuthenticationPrincipal AppUserPrincipal me,
            @Valid @RequestBody ChangeEmailRequest request) {

        return service.requestEmailChange(me.id(), request.newEmail(), request.currentPassword());
    }

    /** The open change waiting for confirmation, or 204 if there's none. */
    @GetMapping("/email-change")
    public ResponseEntity<ProfileService.PendingEmailChange> pendingEmailChange(
            @AuthenticationPrincipal AppUserPrincipal me) {

        return service.pendingEmailChange(me.id())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/email-change")
    public ResponseEntity<Void> cancelEmailChange(@AuthenticationPrincipal AppUserPrincipal me) {
        service.cancelEmailChange(me.id());
        return ResponseEntity.noContent().build();
    }

    /** From the emailed link; works signed in or out. */
    @PostMapping("/email-change/confirm")
    public Map<String, String> confirmEmailChange(@RequestBody ConfirmRequest request) {
        return Map.of("email", service.confirmEmailChange(request.token()));
    }

    public record UpdateProfileRequest(
            @NotNull(message = "Display name is required")
            @Pattern(regexp = "(?s).*\\S.*", message = "Display name must not be blank")
            @Size(max = 120, message = "Display name must not exceed 120 characters")
            String displayName) {

        public UpdateProfileRequest {
            displayName = displayName == null ? null : displayName.trim();
        }
    }

    public record ChangeEmailRequest(
            @NotBlank(message = "New email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 255, message = "Email must not exceed 255 characters")
            String newEmail,
            @NotBlank(message = "Current password is required") String currentPassword) {

        public ChangeEmailRequest {
            newEmail = newEmail == null ? null : newEmail.trim();
        }

        /** Hide the password if this record is ever logged. */
        @Override
        public String toString() {
            return "ChangeEmailRequest[***]";
        }
    }

    public record ConfirmRequest(String token) {

        @Override
        public String toString() {
            return "ConfirmRequest[***]";
        }
    }
}
