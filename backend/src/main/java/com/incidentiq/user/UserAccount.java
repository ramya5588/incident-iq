package com.incidentiq.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role = UserRole.USER;

    /** BCrypt hash; null means the user has no password and cannot sign in. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** False once deactivated: no sign-in, no assignments, no notifications. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    // Which notifications are also sent by email.
    @Column(name = "email_on_assigned", nullable = false)
    private boolean emailOnAssigned = true;

    @Column(name = "email_on_commented", nullable = false)
    private boolean emailOnCommented;

    @Column(name = "email_on_status_changed", nullable = false)
    private boolean emailOnStatusChanged;

    @Column(name = "email_on_sla_breached", nullable = false)
    private boolean emailOnSlaBreached = true;

    @Column(name = "email_on_mentioned", nullable = false)
    private boolean emailOnMentioned = true;

    // Sign-in lockout (see SignInGuard).
    @Column(name = "failed_sign_ins", nullable = false)
    private int failedSignIns;

    @Column(name = "first_failed_sign_in_at")
    private Instant firstFailedSignInAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
        // Required by JPA
    }

    public UserAccount(String email, String displayName, UserRole role) {
        this.email = email;
        this.displayName = displayName;
        this.role = role;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** Takes an already-encoded hash, never a raw password. */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        clearFailedSignIns();
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public void deactivate(Instant now) {
        if (active) {
            active = false;
            deactivatedAt = now;
        }
    }

    public void reactivate() {
        active = true;
        deactivatedAt = null;
    }

    public boolean isEmailOnAssigned() {
        return emailOnAssigned;
    }

    public boolean isEmailOnCommented() {
        return emailOnCommented;
    }

    public boolean isEmailOnStatusChanged() {
        return emailOnStatusChanged;
    }

    public boolean isEmailOnSlaBreached() {
        return emailOnSlaBreached;
    }

    public boolean isEmailOnMentioned() {
        return emailOnMentioned;
    }

    public void setEmailOnMentioned(boolean mentioned) {
        this.emailOnMentioned = mentioned;
    }

    public void setEmailPreferences(
            boolean assigned, boolean commented, boolean statusChanged, boolean slaBreached) {
        this.emailOnAssigned = assigned;
        this.emailOnCommented = commented;
        this.emailOnStatusChanged = statusChanged;
        this.emailOnSlaBreached = slaBreached;
    }

    /**
     * Counts a failed sign-in. After {@code max} within {@code window} the
     * account is locked for {@code lock} and counting starts again.
     *
     * @return true if this failure locked the account
     */
    public boolean recordFailedSignIn(Instant now, java.time.Duration window, int max, java.time.Duration lock) {
        if (firstFailedSignInAt == null || !firstFailedSignInAt.isAfter(now.minus(window))) {
            firstFailedSignInAt = now;
            failedSignIns = 0;
        }
        failedSignIns++;
        if (failedSignIns >= max) {
            lockedUntil = now.plus(lock);
            failedSignIns = 0;
            firstFailedSignInAt = null;
            return true;
        }
        return false;
    }

    /** After a successful sign-in, an admin unlock or a new password. @return whether anything changed */
    public boolean clearFailedSignIns() {
        boolean changed = failedSignIns != 0 || firstFailedSignInAt != null || lockedUntil != null;
        failedSignIns = 0;
        firstFailedSignInAt = null;
        lockedUntil = null;
        return changed;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}