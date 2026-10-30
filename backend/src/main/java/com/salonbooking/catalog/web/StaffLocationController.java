package com.salonbooking.catalog.web;

import com.salonbooking.catalog.app.StaffCatalogService;
import com.salonbooking.catalog.app.StaffCatalogService.StaffLocation;
import com.salonbooking.identity.app.StaffPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Branches for the dashboard's branch picker (DSH-2). The business comes from the session. */
@RestController
class StaffLocationController {

    private final StaffCatalogService catalog;

    StaffLocationController(StaffCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/staff/locations")
    List<StaffLocation> locations(@AuthenticationPrincipal StaffPrincipal staff) {
        return catalog.accessibleLocations(staff);
    }
}
