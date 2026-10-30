package com.salonbooking.identity.app;

import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.shared.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Permission checks for business setup (requirements 4.1). The URL rules only let owners and managers in. */
public final class AdminAccess {

    private AdminAccess() {}

    /** Creating branches, services and staff, invites, business profile. */
    public static void requireOwner(StaffPrincipal staff) {
        if (staff.role() != StaffRole.OWNER) {
            throw forbidden("Only owners can change this.");
        }
    }

    /** Hours, closures and services of one branch: owners, and managers assigned to that branch. */
    public static void requireBranch(StaffPrincipal staff, UUID locationId) {
        if (!canChangeBranch(staff, locationId)) {
            throw forbidden("You can only change branches you manage.");
        }
    }

    /** Owners can change every branch; managers the branches they are assigned to. */
    public static boolean canChangeBranch(StaffPrincipal staff, UUID locationId) {
        return staff.role() == StaffRole.OWNER
                || (staff.role() == StaffRole.MANAGER && staff.canAccessLocation(locationId));
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
