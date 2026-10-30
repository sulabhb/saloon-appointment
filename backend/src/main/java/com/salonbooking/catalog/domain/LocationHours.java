package com.salonbooking.catalog.domain;

import com.salonbooking.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/** One opening interval of a branch on a weekday, in the branch's local time. */
@Entity
@Table(name = "location_hours")
public class LocationHours extends TenantOwnedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    /** ISO weekday: 1 = Monday ... 7 = Sunday. */
    @Column(nullable = false)
    private short weekday;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    protected LocationHours() {}

    public UUID getLocationId() {
        return locationId;
    }

    public int getWeekday() {
        return weekday;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }
}
