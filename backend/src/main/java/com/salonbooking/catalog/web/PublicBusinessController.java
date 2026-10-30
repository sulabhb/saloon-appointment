package com.salonbooking.catalog.web;

import com.salonbooking.catalog.app.PublicCatalogService;
import com.salonbooking.catalog.app.PublicCatalogService.BusinessView;
import com.salonbooking.catalog.app.PublicCatalogService.LocationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public booking entry points. The business in the path is resolved and bound by
 * {@code PublicBusinessTenantFilter} before these methods run, so the slug is not used here.
 */
@RestController
@RequestMapping("/api/v1/public/b/{businessSlug}")
class PublicBusinessController {

    private final PublicCatalogService catalog;

    PublicBusinessController(PublicCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    BusinessView business(@PathVariable String businessSlug) {
        return catalog.getBusiness();
    }

    @GetMapping("/locations/{locationSlug}")
    LocationView location(@PathVariable String businessSlug, @PathVariable String locationSlug) {
        return catalog.getLocation(locationSlug);
    }
}
