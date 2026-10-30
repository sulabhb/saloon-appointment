package com.salonbooking.tenancy;

import com.salonbooking.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base class for every business-owned entity. Hibernate filters reads and fills
 * {@code business_id} on insert from {@link TenantContext}; row-level security enforces the same
 * rule again in the database.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseEntity {

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    public UUID getBusinessId() {
        return businessId;
    }
}
