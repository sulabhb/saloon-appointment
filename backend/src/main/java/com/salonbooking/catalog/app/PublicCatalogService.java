package com.salonbooking.catalog.app;

import com.salonbooking.catalog.data.LocationHoursRepository;
import com.salonbooking.catalog.data.LocationRepository;
import com.salonbooking.catalog.domain.Location;
import com.salonbooking.platform.app.BusinessDirectory;
import com.salonbooking.platform.app.BusinessDirectory.BusinessProfile;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What customers may see about the current business before booking. */
@Service
@Transactional(readOnly = true)
public class PublicCatalogService {

    /** {@code cancelCutoffMin}: customers can cancel online until this many minutes before the start. */
    public record BusinessView(String slug, String displayName, String description, String accentColor,
            int cancelCutoffMin, List<LocationSummary> locations) {}

    public record LocationSummary(String slug, String name, String suburb, String state, String phone,
            String timeZone) {}

    public record LocationView(String slug, String name, String addressLine1, String addressLine2, String suburb,
            String state, String postcode, String phone, String email, String timeZone, List<OpeningHours> hours) {}

    public record OpeningHours(int weekday, LocalTime opensAt, LocalTime closesAt) {}

    private final BusinessDirectory businessDirectory;
    private final LocationRepository locations;
    private final LocationHoursRepository hours;

    PublicCatalogService(BusinessDirectory businessDirectory, LocationRepository locations,
            LocationHoursRepository hours) {
        this.businessDirectory = businessDirectory;
        this.locations = locations;
        this.hours = hours;
    }

    public BusinessView getBusiness() {
        BusinessProfile profile = businessDirectory.getProfile(TenantContext.requireBusinessId());
        List<LocationSummary> summaries = locations.findAllByActiveTrueOrderByName().stream()
                .map(l -> new LocationSummary(l.getSlug(), l.getName(), l.getSuburb(), l.getState(), l.getPhone(),
                        l.getTimeZone()))
                .toList();
        return new BusinessView(profile.slug(), profile.displayName(), profile.description(), profile.accentColor(),
                profile.cancelCutoffMin(), summaries);
    }

    public LocationView getLocation(String locationSlug) {
        Location l = locations.findBySlugAndActiveTrue(locationSlug)
                .orElseThrow(() -> new NotFoundException("Location not found."));
        List<OpeningHours> openingHours = hours.findAllByLocationIdOrderByWeekdayAscOpensAtAsc(l.getId()).stream()
                .map(h -> new OpeningHours(h.getWeekday(), h.getOpensAt(), h.getClosesAt()))
                .toList();
        return new LocationView(l.getSlug(), l.getName(), l.getAddressLine1(), l.getAddressLine2(), l.getSuburb(),
                l.getState(), l.getPostcode(), l.getPhone(), l.getEmail(), l.getTimeZone(), openingHours);
    }
}
