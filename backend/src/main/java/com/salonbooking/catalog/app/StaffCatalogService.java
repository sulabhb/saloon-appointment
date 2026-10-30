package com.salonbooking.catalog.app;

import com.salonbooking.catalog.data.LocationRepository;
import com.salonbooking.identity.app.StaffPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Branch information for signed-in staff, limited to the branches they may work with. */
@Service
@Transactional(readOnly = true)
public class StaffCatalogService {

    public record StaffLocation(UUID id, String slug, String name, String suburb, String state, String timeZone) {}

    private final LocationRepository locations;

    StaffCatalogService(LocationRepository locations) {
        this.locations = locations;
    }

    /** Active branches of the session's business that this person has access to. */
    public List<StaffLocation> accessibleLocations(StaffPrincipal staff) {
        return locations.findAllByActiveTrueOrderByName().stream()
                .filter(l -> staff.canAccessLocation(l.getId()))
                .map(l -> new StaffLocation(l.getId(), l.getSlug(), l.getName(), l.getSuburb(), l.getState(),
                        l.getTimeZone()))
                .toList();
    }
}
