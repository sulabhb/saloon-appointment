package com.salonbooking.identity.domain;

import com.salonbooking.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An invitation for an email address to join the business with a role. Single use. */
@Entity
@Table(name = "invite")
public class Invite extends TenantOwnedEntity {

    public enum Status { PENDING, ACCEPTED, REVOKED, EXPIRED }

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(nullable = false, updatable = false)
    private StaffRole role;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "invited_by_user_id", updatable = false)
    private UUID invitedByUserId;

    /** Staff profile the person is linked to on accepting, if any. */
    @Column(name = "staff_id", updatable = false)
    private UUID staffId;

    @Column(name = "accepted_user_id")
    private UUID acceptedUserId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Invite() {}

    public Invite(String email, StaffRole role, byte[] tokenHash, UUID invitedByUserId, UUID staffId, Instant expiresAt) {
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedByUserId = invitedByUserId;
        this.staffId = staffId;
        this.expiresAt = expiresAt;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public Status statusAt(Instant now) {
        if (acceptedAt != null) {
            return Status.ACCEPTED;
        }
        if (revokedAt != null) {
            return Status.REVOKED;
        }
        return now.isBefore(expiresAt) ? Status.PENDING : Status.EXPIRED;
    }

    public void accept(UUID userId, Instant now) {
        if (statusAt(now) != Status.PENDING) {
            throw new IllegalStateException("Only pending invites can be accepted");
        }
        acceptedUserId = userId;
        acceptedAt = now;
    }

    public void revoke(Instant now) {
        if (statusAt(now) == Status.PENDING) {
            revokedAt = now;
        }
    }

    public String getEmail() {
        return email;
    }

    public StaffRole getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
