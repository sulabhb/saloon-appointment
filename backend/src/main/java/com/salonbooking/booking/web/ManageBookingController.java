package com.salonbooking.booking.web;

import com.salonbooking.booking.app.AvailabilityService;
import com.salonbooking.booking.app.AvailabilityService.DayStatus;
import com.salonbooking.booking.app.AvailabilityService.Request;
import com.salonbooking.booking.app.BookingService;
import com.salonbooking.booking.app.BookingService.BookingView;
import com.salonbooking.booking.app.BookingService.RescheduleOptions;
import com.salonbooking.booking.app.BookingService.TokenTarget;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's private management link (MGT-1..5). The token names the business, so it is
 * resolved first and the business bound before any transaction starts.
 */
@RestController
@RequestMapping("/api/v1/public/manage/{token}")
class ManageBookingController {

    record Slot(Instant start, LocalTime time) {}

    record Slots(LocalDate date, String timeZone, int durationMin, List<Slot> slots) {}

    record Day(LocalDate date, DayStatus status) {}

    record Days(List<Day> days, LocalDate lastBookableDate) {}

    /** {@code staffId} absent means anyone free; the current stylist is kept when possible. */
    record RescheduleRequest(@NotNull Instant start, UUID staffId) {}

    private static final int MAX_DAYS = 62;

    private final BookingService bookings;
    private final AvailabilityService availability;

    ManageBookingController(BookingService bookings, AvailabilityService availability) {
        this.bookings = bookings;
        this.availability = availability;
    }

    @GetMapping
    BookingView view(@PathVariable String token) {
        return inBusiness(token, bookings::view);
    }

    @PostMapping("/cancel")
    BookingView cancel(@PathVariable String token) {
        return inBusiness(token, bookings::cancelByCustomer);
    }

    @GetMapping("/options")
    RescheduleOptions options(@PathVariable String token) {
        return inBusiness(token, bookings::rescheduleOptions);
    }

    @GetMapping("/availability")
    Slots slots(@PathVariable String token, @RequestParam LocalDate date, @RequestParam(required = false) UUID staff) {
        return inBusiness(token, id -> {
            Request request = bookings.rescheduleRequest(id, staff);
            return new Slots(date, request.branch().zone().getId(), request.durationMin(), availability.slots(request, date)
                    .stream().map(s -> new Slot(s.start(), s.localTime())).toList());
        });
    }

    @GetMapping("/availability/days")
    Days days(@PathVariable String token, @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) UUID staff) {
        if (to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Ask for at most %d days.".formatted(MAX_DAYS));
        }
        return inBusiness(token, id -> {
            Request request = bookings.rescheduleRequest(id, staff);
            return new Days(availability.days(request, from, to).entrySet().stream()
                    .map(e -> new Day(e.getKey(), e.getValue())).toList(), availability.lastBookableDate(request.branch()));
        });
    }

    /** Same branch and services at a new time; the original time stands if the new one is gone (MGT-4). */
    @PostMapping("/reschedule")
    BookingView reschedule(@PathVariable String token, @Valid @RequestBody RescheduleRequest request) {
        return inBusiness(token, id -> bookings.rescheduleByCustomer(id, request.start(), request.staffId()));
    }

    private <T> T inBusiness(String token, Function<UUID, T> action) {
        TokenTarget target = resolve(token);
        return TenantContext.callForBusiness(target.businessId(), () -> action.apply(target.appointmentId()));
    }

    private TokenTarget resolve(String token) {
        if (token.length() > 100) {
            throw invalidLink();
        }
        return bookings.resolve(token).orElseThrow(ManageBookingController::invalidLink);
    }

    private static NotFoundException invalidLink() {
        return new NotFoundException("This link has expired or is not valid. Contact the salon if you need help.");
    }
}
