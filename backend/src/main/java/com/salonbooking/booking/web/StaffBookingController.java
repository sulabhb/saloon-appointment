package com.salonbooking.booking.web;

import com.salonbooking.booking.app.AppointmentService;
import com.salonbooking.booking.app.AppointmentService.AppointmentDetail;
import com.salonbooking.booking.app.AppointmentService.CustomerDetail;
import com.salonbooking.booking.app.AppointmentService.CustomerMatch;
import com.salonbooking.booking.app.AppointmentService.DayCalendar;
import com.salonbooking.booking.app.AppointmentService.Status;
import com.salonbooking.booking.app.AvailabilityService;
import com.salonbooking.booking.app.AvailabilityService.Request;
import com.salonbooking.booking.app.BookingCatalogService;
import com.salonbooking.booking.app.BookingCatalogService.BranchCatalog;
import com.salonbooking.booking.app.BookingService;
import com.salonbooking.booking.app.BookingService.StaffBooking;
import com.salonbooking.identity.app.StaffPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bookings from the staff side (DSH-4, DSH-8..16, STS). Permissions are checked in the services. */
@RestController
@RequestMapping("/api/v1/staff")
class StaffBookingController {

    /** Staff see which stylists are free at each time, so they can choose together (DSH-9). */
    record StaffSlot(Instant start, LocalTime time, List<UUID> staffIds) {}

    record StaffSlots(LocalDate date, String timeZone, int durationMin, long totalPriceCents, List<StaffSlot> slots) {}

    record NewCustomer(@Size(max = 120) String fullName, @Size(max = 200) String email, @Size(max = 30) String phone) {}

    record CreateRequest(
            @NotNull UUID locationId,
            @NotEmpty @Size(max = 10) List<@NotNull UUID> serviceIds,
            UUID staffId,
            @NotNull Instant start,
            UUID customerId,
            @Valid NewCustomer customer,
            @Size(max = 500) String note,
            @Size(max = 2000) String internalNote,
            boolean walkIn,
            @NotNull UUID idempotencyKey) {}

    record StatusRequest(@NotNull Status status, @Size(max = 500) String reason) {}

    record RescheduleRequest(@NotNull Instant start, UUID staffId) {}

    record NoteRequest(@Size(max = 2000) String internalNote) {}

    private final BookingCatalogService catalog;
    private final AvailabilityService availability;
    private final BookingService bookings;
    private final AppointmentService appointments;

    StaffBookingController(BookingCatalogService catalog, AvailabilityService availability, BookingService bookings,
            AppointmentService appointments) {
        this.catalog = catalog;
        this.availability = availability;
        this.bookings = bookings;
        this.appointments = appointments;
    }

    @GetMapping("/booking-options")
    BranchCatalog options(@AuthenticationPrincipal StaffPrincipal staff, @RequestParam UUID locationId) {
        AppointmentService.requireBooker(staff, locationId);
        return catalog.staffCatalog(locationId);
    }

    @GetMapping("/availability")
    StaffSlots availability(@AuthenticationPrincipal StaffPrincipal staff, @RequestParam UUID locationId,
            @RequestParam List<UUID> services, @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) UUID reschedule, @RequestParam LocalDate date) {
        AppointmentService.requireBooker(staff, locationId);
        Request request = availability.staffRequest(locationId, services, staffId);
        if (reschedule != null) {
            request = request.ignoring(reschedule);
        }
        List<StaffSlot> slots = availability.slots(request, date).stream()
                .map(s -> new StaffSlot(s.start(), s.localTime(), s.staffIds())).toList();
        return new StaffSlots(date, request.branch().zone().getId(), request.durationMin(), request.totalPriceCents(), slots);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentDetail create(@AuthenticationPrincipal StaffPrincipal staff, @Valid @RequestBody CreateRequest request) {
        AppointmentService.requireBooker(staff, request.locationId());
        NewCustomer customer = request.customer() == null ? new NewCustomer(null, null, null) : request.customer();
        UUID id = bookings.createByStaff(new StaffBooking(request.locationId(), request.serviceIds(), request.staffId(),
                request.start(), request.customerId(), customer.fullName(), customer.email(), customer.phone(),
                request.note(), request.internalNote(), request.walkIn(), request.idempotencyKey(), staff.userId()));
        return appointments.detail(staff, id);
    }

    @GetMapping("/appointments/{id}")
    AppointmentDetail detail(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID id) {
        return appointments.detail(staff, id);
    }

    @PostMapping("/appointments/{id}/status")
    AppointmentDetail status(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID id,
            @Valid @RequestBody StatusRequest request) {
        return appointments.changeStatus(staff, id, request.status(), request.reason());
    }

    @PostMapping("/appointments/{id}/reschedule")
    AppointmentDetail reschedule(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID id,
            @Valid @RequestBody RescheduleRequest request) {
        return appointments.reschedule(staff, id, request.start(), request.staffId());
    }

    @PutMapping("/appointments/{id}/note")
    AppointmentDetail note(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        return appointments.updateNote(staff, id, request.internalNote());
    }

    @GetMapping("/calendar")
    DayCalendar calendar(@AuthenticationPrincipal StaffPrincipal staff, @RequestParam UUID locationId,
            @RequestParam LocalDate date) {
        return appointments.day(staff, locationId, date);
    }

    /** The week (Monday to Sunday) containing {@code date}, one day calendar per date. */
    @GetMapping("/calendar/week")
    List<DayCalendar> week(@AuthenticationPrincipal StaffPrincipal staff, @RequestParam UUID locationId,
            @RequestParam LocalDate date) {
        return appointments.week(staff, locationId, date);
    }

    @GetMapping("/customers")
    List<CustomerMatch> customers(@AuthenticationPrincipal StaffPrincipal staff, @RequestParam String q) {
        return appointments.searchCustomers(staff, q);
    }

    @GetMapping("/customers/{id}")
    CustomerDetail customer(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID id) {
        return appointments.customer(staff, id);
    }
}
