package com.salonbooking.identity.app;

import com.salonbooking.identity.domain.SessionClient;
import com.salonbooking.identity.domain.StaffRole;
import java.util.Set;
import java.util.UUID;

/**
 * The signed-in person for the current request.
 *
 * @param businessId the business selected for this session, or null until one is chosen
 * @param role role in that business, or null when none is selected
 * @param locationIds branches the person may work with; null means all branches of the business
 * @param staffId the person's own staff profile in that business, if they perform services
 */
public record StaffPrincipal(
        UUID userId,
        String email,
        UUID sessionId,
        SessionClient client,
        boolean platformAdmin,
        UUID businessId,
        StaffRole role,
        Set<UUID> locationIds,
        UUID staffId) {

    public boolean hasBusiness() {
        return businessId != null;
    }

    public boolean canAccessLocation(UUID locationId) {
        return hasBusiness() && (locationIds == null || locationIds.contains(locationId));
    }
}
