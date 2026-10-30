package com.salonbooking.identity.domain;

import com.salonbooking.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/** A person who can sign in. A platform table: one account can belong to several businesses. */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "is_platform_admin", nullable = false)
    private boolean platformAdmin;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected AppUser() {}

    public AppUser(String email, String fullName, String passwordHash) {
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.active = true;
    }

    public void grantPlatformAdmin() {
        this.platformAdmin = true;
    }

    public boolean canSignIn(Instant now) {
        return active && passwordHash != null && (lockedUntil == null || !lockedUntil.isAfter(now));
    }

    /** Counts a wrong password; locks the account for {@code lockout} after {@code maxFailures} in a row. */
    public void recordFailedLogin(Instant now, int maxFailures, Duration lockout) {
        failedLogins++;
        if (failedLogins >= maxFailures) {
            lockedUntil = now.plus(lockout);
            failedLogins = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedLogins = 0;
        lockedUntil = null;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public boolean isActive() {
        return active;
    }
}
