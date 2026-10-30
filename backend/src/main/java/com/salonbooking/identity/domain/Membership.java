package com.salonbooking.identity.domain;

import com.salonbooking.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A person's role in one business. Branch assignments live in membership_location. */
@Entity
@Table(name = "membership")
public class Membership extends TenantOwnedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private StaffRole role;

    @Column(nullable = false)
    private boolean active;

    /** The person's own staff profile, if they also take bookings. */
    @Column(name = "staff_id")
    private UUID staffId;

    protected Membership() {}

    public Membership(UUID userId, StaffRole role, UUID staffId) {
        this.userId = userId;
        this.role = role;
        this.staffId = staffId;
        this.active = true;
    }

    /** Re-admits a former member with the invited role and profile. */
    public void reactivate(StaffRole newRole, UUID newStaffId) {
        this.role = newRole;
        this.active = true;
        if (newStaffId != null) {
            this.staffId = newStaffId;
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public UUID getUserId() {
        return userId;
    }

    public StaffRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
