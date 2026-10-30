package com.salonbooking.platform.domain;

import com.salonbooking.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** A salon business (tenant). A platform table: not business-owned itself. */
@Entity
@Table(name = "business")
public class Business extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private BusinessStatus status;

    @Column(name = "default_time_zone", nullable = false)
    private String defaultTimeZone;

    @Column(name = "accent_color")
    private String accentColor;

    private String description;

    @Column(name = "cancel_cutoff_min", nullable = false, insertable = false, updatable = false)
    private int cancelCutoffMin;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Business() {}

    /** Moves the business through its lifecycle; returns false if the change is not allowed. */
    public boolean changeStatus(BusinessStatus target, Instant now) {
        if (!status.canChangeTo(target)) {
            return false;
        }
        status = target;
        if (target == BusinessStatus.CLOSED) {
            closedAt = now;
        }
        return true;
    }

    public String getSlug() {
        return slug;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BusinessStatus getStatus() {
        return status;
    }

    public String getDefaultTimeZone() {
        return defaultTimeZone;
    }

    public String getAccentColor() {
        return accentColor;
    }

    /** Minutes before the start after which customers cannot cancel online (D-9). */
    public int getCancelCutoffMin() {
        return cancelCutoffMin;
    }

    public String getDescription() {
        return description;
    }
}
