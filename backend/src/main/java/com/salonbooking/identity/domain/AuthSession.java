package com.salonbooking.identity.domain;

import com.salonbooking.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** One signed-in browser or device. See V2__auth_session.sql. */
@Entity
@Table(name = "auth_session")
public class AuthSession extends BaseEntity {

    /** Sliding expiry is written at most this often, so not every request updates the row. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "selected_business_id")
    private UUID selectedBusinessId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(nullable = false, updatable = false)
    private SessionClient client;

    @Column(name = "device_name", updatable = false)
    private String deviceName;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false, updatable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AuthSession() {}

    public AuthSession(UUID userId, UUID selectedBusinessId, byte[] tokenHash, SessionClient client,
            String deviceName, Instant now, Duration idleTimeout, Duration absoluteTimeout) {
        this.userId = userId;
        this.selectedBusinessId = selectedBusinessId;
        this.tokenHash = tokenHash;
        this.client = client;
        this.deviceName = deviceName;
        this.lastSeenAt = now;
        this.absoluteExpiresAt = now.plus(absoluteTimeout);
        this.idleExpiresAt = min(now.plus(idleTimeout), absoluteExpiresAt);
    }

    public boolean isValidAt(Instant now) {
        return revokedAt == null && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
    }

    /** Extends the idle expiry; returns true if the row changed and should be saved. */
    public boolean touch(Instant now, Duration idleTimeout) {
        if (Duration.between(lastSeenAt, now).compareTo(TOUCH_INTERVAL) < 0) {
            return false;
        }
        lastSeenAt = now;
        idleExpiresAt = min(now.plus(idleTimeout), absoluteExpiresAt);
        return true;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void selectBusiness(UUID businessId) {
        this.selectedBusinessId = businessId;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSelectedBusinessId() {
        return selectedBusinessId;
    }

    public SessionClient getClient() {
        return client;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }
}
