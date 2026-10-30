package com.salonbooking.booking.web;

import com.salonbooking.booking.app.AvailabilityService;
import com.salonbooking.booking.app.AvailabilityService.DayStatus;
import com.salonbooking.booking.app.AvailabilityService.Request;
import com.salonbooking.booking.app.BookingCatalogService;
import com.salonbooking.booking.app.BookingCatalogService.BranchCatalog;
import com.salonbooking.booking.app.BookingService;
import com.salonbooking.booking.app.BookingService.Created;
import com.salonbooking.booking.app.BookingService.NewBooking;
import com.salonbooking.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Online booking (BKG, AVL). The business in the path is bound by {@code PublicBusinessTenantFilter},
 * which only lets Live businesses through. {@code staff} is a stylist id, or absent for "any".
 */
@RestController
@RequestMapping("/api/v1/public/b/{businessSlug}")
class PublicBookingController {

    record Slot(Instant start, LocalTime time) {}

    record Slots(LocalDate date, String timeZone, int durationMin, long totalPriceCents, List<Slot> slots) {}

    record Day(LocalDate date, DayStatus status) {}

    record Days(List<Day> days, LocalDate lastBookableDate) {}

    record Customer(@NotBlank @Size(max = 120) String fullName, @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(max = 30) String phone) {}

    record BookingRequest(
            @NotBlank String locationSlug,
            @NotEmpty @Size(max = 10) List<@NotNull UUID> serviceIds,
            UUID staffId,
            @NotNull Instant start,
            @NotNull @Valid Customer customer,
            @Size(max = 500) String note,
            @AssertTrue boolean acceptsPolicy,
            @NotNull UUID idempotencyKey) {}

    record RecoverRequest(@NotBlank @Size(max = 20) String reference, @NotBlank @Email @Size(max = 200) String email) {}

    record Accepted(String message) {}

    private static final int MAX_DAYS = 62;

    private final BookingCatalogService catalog;
    private final AvailabilityService availability;
    private final BookingService bookings;

    PublicBookingController(BookingCatalogService catalog, AvailabilityService availability, BookingService bookings) {
        this.catalog = catalog;
        this.availability = availability;
        this.bookings = bookings;
    }

    @GetMapping("/locations/{locationSlug}/catalog")
    BranchCatalog catalog(@PathVariable String businessSlug, @PathVariable String locationSlug) {
        return catalog.catalog(locationSlug);
    }

    @GetMapping("/locations/{locationSlug}/availability")
    Slots slots(@PathVariable String businessSlug, @PathVariable String locationSlug,
            @RequestParam List<UUID> services, @RequestParam(required = false) UUID staff, @RequestParam LocalDate date) {
        Request request = availability.request(locationSlug, services, staff);
        // Stylists stay hidden for "any" until the booking is made (AVL-7).
        List<Slot> slots = availability.slots(request, date).stream().map(s -> new Slot(s.start(), s.localTime())).toList();
        return new Slots(date, request.branch().zone().getId(), request.durationMin(), request.totalPriceCents(), slots);
    }

    @GetMapping("/locations/{locationSlug}/availability/days")
    Days days(@PathVariable String businessSlug, @PathVariable String locationSlug,
            @RequestParam List<UUID> services, @RequestParam(required = false) UUID staff,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Ask for at most %d days.".formatted(MAX_DAYS));
        }
        Request request = availability.request(locationSlug, services, staff);
        List<Day> days = availability.days(request, from, to).entrySet().stream()
                .map(e -> new Day(e.getKey(), e.getValue())).toList();
        return new Days(days, availability.lastBookableDate(request.branch()));
    }

    /** Same response whether or not anything matched, so it reveals nothing (MGT-6, MGT-7). */
    @PostMapping("/manage/recover")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Accepted recover(@PathVariable String businessSlug, @Valid @RequestBody RecoverRequest request) {
        bookings.recoverLink(request.reference(), request.email());
        return new Accepted("If that matches a booking, we've emailed a new link to the address on file.");
    }

    /** Safe to retry with the same idempotency key: the same booking comes back (BKG-10). */
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    Created book(@PathVariable String businessSlug, @Valid @RequestBody BookingRequest request) {
        return bookings.create(new NewBooking(request.locationSlug(), request.serviceIds(), request.staffId(),
                request.start(), request.customer().fullName(), request.customer().email(), request.customer().phone(),
                request.note(), request.idempotencyKey(), "web"));
    }
}
