package com.incidentiq.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Forgot password?" for signed-out users. The token travels in request
 * bodies (never in URLs), so it doesn't end up in access logs.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    static final String SENT_MESSAGE =
            "If an account with that email exists, we've sent it a link to reset the password.";

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    /** Whether to offer "Forgot password?" (it needs email). */
    @GetMapping
    public Map<String, Boolean> availability() {
        return Map.of("available", service.available());
    }

    /** Always the same answer, whether or not the account exists. */
    @PostMapping
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody ResetLinkRequest request) {
        service.requestReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message", SENT_MESSAGE));
    }

    /** 204 if the link can still be used, 400 if not. */
    @PostMapping("/check")
    public ResponseEntity<Void> check(@RequestBody TokenRequest request) {
        service.check(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmResetRequest request) {
        service.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record ResetLinkRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 255) String email) {

        public ResetLinkRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record TokenRequest(String token) {

        @Override
        public String toString() {
            return "TokenRequest[***]";
        }
    }

    public record ConfirmResetRequest(
            @NotBlank(message = "The reset link is missing its token") String token,
            // BCrypt only uses the first 72 bytes, so cap it there.
            @NotBlank(message = "New password is required")
            @Size(min = 8, max = 72, message = "New password must be 8 to 72 characters")
            String newPassword) {

        /** Hide the token and password if this record is ever logged. */
        @Override
        public String toString() {
            return "ConfirmResetRequest[***]";
        }
    }
}
