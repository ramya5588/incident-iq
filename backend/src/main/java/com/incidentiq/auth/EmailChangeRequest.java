package com.incidentiq.auth;

import java.time.Instant;

import com.incidentiq.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A requested change of sign-in email, waiting to be confirmed from the new address. */
@Entity
@Table(name = "email_change_requests")
public class EmailChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserAccount user;

    @Column(name = "new_email", nullable = false, updatable = false)
    private String newEmail;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** When it was confirmed, or cancelled. */
    @Column(name = "used_at")
    private Instant usedAt;

    protected EmailChangeRequest() {
    }

    EmailChangeRequest(
            UserAccount user, String newEmail, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.newEmail = newEmail;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean usableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt) && user.isActive();
    }

    UserAccount getUser() {
        return user;
    }

    String getNewEmail() {
        return newEmail;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }
}
