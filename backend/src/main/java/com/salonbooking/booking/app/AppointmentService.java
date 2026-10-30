package com.salonbooking.booking.app;

import com.salonbooking.booking.app.AvailabilityService.Request;
import com.salonbooking.booking.domain.Contacts;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.notification.app.NotificationQueue;
import com.salonbooking.notification.app.NotificationType;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.staff.domain.ScheduleRules;
import com.salonbooking.staff.domain.ScheduleRules.WeeklyShift;
import com.salonbooking.staff.domain.ScheduleRules.WorkInterval;
import com.salonbooking.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appointments from the staff side: detail, status changes (STS), reschedule (DSH-11), internal
 * notes (DSH-16), the day calendar (DSH-4) and customer search (DSH-13, DSH-14). Permissions
 * follow requirements 4.1: stylists see and update only their own appointments.
 */
@Service
@Transactional
public class AppointmentService {

    public enum Status { CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW }

    public record ServiceLine(UUID serviceId, String name, int durationMin, long priceCents, boolean priceIsFrom) {}

    public record CustomerRef(UUID id, String fullName, String email, String phone) {}

    /** An email about this appointment and whether it went out (NTF-5, NTF-6: "sent" means accepted). */
    public record EmailRecord(String type, String status, String recipient, int attempts, String lastError,
            Instant sentAt, Instant createdAt) {}

    /** Everything staff see about one appointment (DSH-8). */
    public record AppointmentDetail(UUID id, String reference, Status status, Instant startAt, Instant endAt,
            UUID locationId, String locationName, String timeZone, UUID staffId, String staffName, CustomerRef customer,
            List<ServiceLine> services, long totalPriceCents, String customerNote, String internalNote, String source,
            Instant createdAt, String createdByName, Instant statusChangedAt, String cancelledBy, String cancelReason,
            boolean lateCancel, List<Status> allowedTransitions, boolean canReschedule, boolean canEditNote,
            List<EmailRecord> emails) {}

    public record CalendarAppointment(UUID id, String reference, Status status, Instant startAt, Instant endAt,
            Instant blockedUntil, String customerName, String services, boolean hasNote, boolean elsewhere,
            String locationName) {}

    public record CalendarTimeOff(String kind, LocalTime from, LocalTime to, String reason) {}

    /** One stylist's day at the branch: working intervals, time off and appointments (DSH-4). */
    public record CalendarColumn(UUID staffId, String displayName, boolean bookableOnline, List<LocalTime[]> work,
            List<CalendarTimeOff> timeOff, List<CalendarAppointment> appointments) {}

    public record DayCalendar(LocalDate date, UUID locationId, String timeZone, List<LocalTime[]> opening,
            String closureReason, boolean closed, List<CalendarColumn> columns) {}

    public record CustomerMatch(UUID id, String fullName, String email, String phone, Instant lastVisit,
            int upcoming) {}

    public record CustomerHistoryItem(UUID id, String reference, Status status, Instant startAt, String timeZone,
            String locationName, String staffName, String services, boolean lateCancel) {}

    public record CustomerDetail(UUID id, String fullName, String email, String phone, Instant since, int noShows,
            int lateCancels, List<CustomerHistoryItem> appointments) {}

    private record Row(UUID id, Status status, Instant start, UUID staffId, UUID locationId, ZoneId zone,
            int cancelCutoffMin) {}

    private final JdbcClient jdbc;
    private final AvailabilityService availability;
    private final NotificationQueue notifications;
    private final AppointmentMover mover;
    private final Clock clock;

    AppointmentService(JdbcClient jdbc, AvailabilityService availability, NotificationQueue notifications,
            AppointmentMover mover, Clock clock) {
        this.jdbc = jdbc;
        this.availability = availability;
        this.notifications = notifications;
        this.mover = mover;
        this.clock = clock;
    }

    // ---- Access ---------------------------------------------------------------------------------

    /** Reception, managers and owners create, reschedule and cancel at branches they can access. */
    public static void requireBooker(StaffPrincipal staff, UUID locationId) {
        if (staff.role() == StaffRole.STYLIST || !staff.canAccessLocation(locationId)) {
            throw forbidden("You can't make bookings at this branch.");
        }
    }

    private static boolean managerOrOwner(StaffPrincipal staff) {
        return staff.role() == StaffRole.OWNER || staff.role() == StaffRole.MANAGER;
    }

    /** Stylists see their own appointments; everyone else those at branches they can access. */
    private static boolean canSee(StaffPrincipal staff, UUID locationId, UUID staffId) {
        return staff.canAccessLocation(locationId) && (staff.role() != StaffRole.STYLIST || staffId.equals(staff.staffId()));
    }

    private Row row(StaffPrincipal staff, UUID appointmentId) {
        Row row = jdbc.sql("""
                        SELECT a.id, a.status, a.start_at, a.staff_id, a.location_id, l.time_zone, b.cancel_cutoff_min
                        FROM appointment a JOIN location l ON l.id = a.location_id JOIN business b ON b.id = a.business_id
                        WHERE a.business_id = :businessId AND a.id = :id""")
                .param("businessId", business()).param("id", appointmentId)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), status(rs.getString("status")),
                        rs.getTimestamp("start_at").toInstant(), rs.getObject("staff_id", UUID.class),
                        rs.getObject("location_id", UUID.class), ZoneId.of(rs.getString("time_zone")),
                        rs.getInt("cancel_cutoff_min")))
                .optional()
                .orElseThrow(() -> new NotFoundException("Appointment not found."));
        if (!canSee(staff, row.locationId(), row.staffId())) {
            throw new NotFoundException("Appointment not found.");
        }
        return row;
    }

    // ---- Status (STS-2) ------------------------------------------------------------------------

    /** The transitions this person may make now, in the order the console offers them. */
    private List<Status> allowed(StaffPrincipal staff, Row row) {
        boolean booker = staff.role() != StaffRole.STYLIST;
        boolean started = !clock.instant().isBefore(row.start());
        boolean sameDay = LocalDate.now(clock.withZone(row.zone())).equals(row.start().atZone(row.zone()).toLocalDate());
        List<Status> result = new ArrayList<>();
        switch (row.status()) {
            case CONFIRMED -> {
                result.add(Status.CHECKED_IN);
                if (started) {
                    result.add(Status.NO_SHOW);
                }
                if (booker) {
                    result.add(Status.CANCELLED);
                }
            }
            case CHECKED_IN -> {
                result.add(Status.COMPLETED);
                if (managerOrOwner(staff)) {
                    result.add(Status.CANCELLED);
                }
            }
            case NO_SHOW -> {
                if (managerOrOwner(staff) && sameDay) {
                    result.add(Status.CONFIRMED);
                }
            }
            case COMPLETED -> {
                if (managerOrOwner(staff) && sameDay) {
                    result.add(Status.CHECKED_IN);
                }
            }
            case CANCELLED -> { }
        }
        return result;
    }

    public AppointmentDetail changeStatus(StaffPrincipal staff, UUID appointmentId, Status target, String reason) {
        Row row = row(staff, appointmentId);
        if (!allowed(staff, row).contains(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "%s appointments can't be changed to %s%s."
                    .formatted(label(row.status()), label(target), target == Status.NO_SHOW && row.status() == Status.CONFIRMED
                            ? " before they start" : ""));
        }
        boolean late = target == Status.CANCELLED
                && clock.instant().isAfter(row.start().minusSeconds(row.cancelCutoffMin() * 60L));
        // Undoing a no-show takes the time back, which fails if it has been rebooked (STS-4).
        Optional<String> failure = Savepoints.attempt(jdbc, () -> jdbc.sql("""
                        UPDATE appointment SET status = :status, status_changed_at = now(), updated_at = now(),
                               cancelled_at = CASE WHEN :status = 'cancelled' THEN now() ELSE cancelled_at END,
                               cancelled_by = CASE WHEN :status = 'cancelled' THEN 'staff' ELSE cancelled_by END,
                               cancel_reason = CASE WHEN :status = 'cancelled' THEN :reason ELSE cancel_reason END,
                               late_cancel = CASE WHEN :status = 'cancelled' THEN :late ELSE late_cancel END
                        WHERE business_id = :businessId AND id = :id""")
                .param("status", target.name().toLowerCase(Locale.ROOT))
                .param("reason", reason == null || reason.isBlank() ? null : reason.trim())
                .param("late", late)
                .param("businessId", business()).param("id", appointmentId)
                .update());
        if (failure.isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                    "That time has been booked by someone else since, so this can't be undone.");
        }
        if (target == Status.CANCELLED) {
            notifications.queue(appointmentId, NotificationType.BOOKING_CANCELLED);
            notifications.dropPendingReminders(appointmentId);
        }
        return detail(staff, appointmentId);
    }

    // ---- Reschedule (DSH-11, MGT-4) -------------------------------------------------------------

    /** Same services and branch at a new time, same or another stylist. The old time stands if this fails. */
    public AppointmentDetail reschedule(StaffPrincipal staff, UUID appointmentId, Instant start, UUID staffId) {
        Row row = row(staff, appointmentId);
        requireBooker(staff, row.locationId());
        if (row.status() != Status.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only confirmed appointments can be moved.");
        }
        Request request = availability.staffRequest(row.locationId(), mover.serviceIds(appointmentId), staffId)
                .ignoring(appointmentId);
        mover.move(appointmentId, request, start, row.staffId());
        return detail(staff, appointmentId);
    }

    // ---- Detail and notes -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AppointmentDetail detail(StaffPrincipal staff, UUID appointmentId) {
        Row row = row(staff, appointmentId);
        UUID businessId = business();
        List<ServiceLine> services = jdbc.sql("""
                        SELECT service_id, name, duration_min, price, price_is_from FROM appointment_service
                        WHERE business_id = :businessId AND appointment_id = :id ORDER BY position""")
                .param("businessId", businessId).param("id", appointmentId)
                .query((rs, n) -> new ServiceLine(rs.getObject("service_id", UUID.class), rs.getString("name"),
                        rs.getInt("duration_min"), rs.getLong("price"),
                        rs.getBoolean("price_is_from")))
                .list();
        List<Status> allowed = allowed(staff, row);
        boolean booker = staff.role() != StaffRole.STYLIST;
        List<EmailRecord> emails = jdbc.sql("""
                        SELECT type, status, recipient, attempts, last_error, sent_at, created_at FROM notification
                        WHERE business_id = :businessId AND appointment_id = :id AND channel = 'email' ORDER BY created_at""")
                .param("businessId", businessId).param("id", appointmentId)
                .query((rs, n) -> new EmailRecord(rs.getString("type"), rs.getString("status"), rs.getString("recipient"),
                        rs.getInt("attempts"), rs.getString("last_error"),
                        rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
        return jdbc.sql("""
                        SELECT a.reference, a.end_at, l.name AS location_name, st.display_name AS staff_name,
                               c.id AS customer_id, c.full_name, c.email, c.phone_e164, a.customer_note, a.internal_note,
                               a.source, a.created_at, u.full_name AS created_by, a.status_changed_at, a.cancelled_by,
                               a.cancel_reason, a.late_cancel
                        FROM appointment a
                        JOIN location l ON l.id = a.location_id
                        JOIN staff st ON st.id = a.staff_id
                        JOIN customer c ON c.id = a.customer_id
                        LEFT JOIN app_user u ON u.id = a.created_by_user_id
                        WHERE a.business_id = :businessId AND a.id = :id""")
                .param("businessId", businessId).param("id", appointmentId)
                .query((rs, n) -> new AppointmentDetail(appointmentId, rs.getString("reference"), row.status(), row.start(),
                        rs.getTimestamp("end_at").toInstant(), row.locationId(), rs.getString("location_name"),
                        row.zone().getId(), row.staffId(), rs.getString("staff_name"),
                        new CustomerRef(rs.getObject("customer_id", UUID.class), rs.getString("full_name"),
                                rs.getString("email"), rs.getString("phone_e164")),
                        services, services.stream().mapToLong(ServiceLine::priceCents).sum(), rs.getString("customer_note"),
                        rs.getString("internal_note"), rs.getString("source"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("created_by"),
                        rs.getTimestamp("status_changed_at") == null ? null : rs.getTimestamp("status_changed_at").toInstant(),
                        rs.getString("cancelled_by"), rs.getString("cancel_reason"), rs.getBoolean("late_cancel"),
                        allowed, booker && row.status() == Status.CONFIRMED, booker, emails))
                .single();
    }

    /** Internal notes: stylists read their own, everyone else reads and writes (DSH-16). */
    public AppointmentDetail updateNote(StaffPrincipal staff, UUID appointmentId, String note) {
        Row row = row(staff, appointmentId);
        requireBooker(staff, row.locationId());
        jdbc.sql("UPDATE appointment SET internal_note = :note, updated_at = now() WHERE business_id = :businessId AND id = :id")
                .param("note", note == null || note.isBlank() ? null : note.trim())
                .param("businessId", business()).param("id", appointmentId)
                .update();
        return detail(staff, appointmentId);
    }

    // ---- Day calendar (DSH-4) -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DayCalendar day(StaffPrincipal staff, UUID locationId, LocalDate date) {
        UUID businessId = business();
        if (!staff.canAccessLocation(locationId)) {
            throw new NotFoundException("Branch not found.");
        }
        ZoneId zone = jdbc.sql("SELECT time_zone FROM location WHERE business_id = :businessId AND id = :id")
                .param("businessId", businessId).param("id", locationId)
                .query(String.class).optional().map(ZoneId::of)
                .orElseThrow(() -> new NotFoundException("Branch not found."));

        List<LocalTime[]> opening = jdbc.sql("""
                        SELECT opens_at, closes_at FROM location_hours
                        WHERE business_id = :businessId AND location_id = :id AND weekday = :weekday ORDER BY opens_at""")
                .param("businessId", businessId).param("id", locationId).param("weekday", date.getDayOfWeek().getValue())
                .query((rs, n) -> new LocalTime[] {rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class)})
                .list();
        Optional<String> closure = jdbc.sql("""
                        SELECT coalesce(reason, '') FROM location_closure
                        WHERE business_id = :businessId AND location_id = :id AND :date BETWEEN starts_on AND ends_on LIMIT 1""")
                .param("businessId", businessId).param("id", locationId).param("date", date)
                .query(String.class).optional();

        record Person(UUID id, String name, boolean online) {}
        List<Person> people = jdbc.sql("""
                        SELECT st.id, st.display_name, st.bookable_online FROM staff st
                        JOIN staff_location sl ON sl.staff_id = st.id AND sl.location_id = :locationId
                        WHERE st.business_id = :businessId AND st.active ORDER BY st.display_name""")
                .param("businessId", businessId).param("locationId", locationId)
                .query((rs, n) -> new Person(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getBoolean("bookable_online")))
                .list().stream()
                .filter(p -> staff.role() != StaffRole.STYLIST || p.id().equals(staff.staffId()))
                .toList();
        if (people.isEmpty()) {
            return new DayCalendar(date, locationId, zone.getId(), opening, closure.orElse(null), closure.isPresent(), List.of());
        }
        List<UUID> ids = people.stream().map(Person::id).toList();

        Map<UUID, List<WeeklyShift>> patterns = jdbc.sql("""
                        SELECT staff_id, weekday, location_id, starts_at, ends_at FROM staff_shift
                        WHERE business_id = :businessId AND staff_id IN (:ids) AND weekday = :weekday""")
                .param("businessId", businessId).param("ids", ids).param("weekday", date.getDayOfWeek().getValue())
                .query((rs, n) -> Map.entry(rs.getObject("staff_id", UUID.class), new WeeklyShift(rs.getInt("weekday"),
                        rs.getObject("location_id", UUID.class), rs.getObject("starts_at", LocalTime.class),
                        rs.getObject("ends_at", LocalTime.class))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<UUID, List<WorkInterval>> overrides = new HashMap<>();
        jdbc.sql("""
                        SELECT staff_id, kind, location_id, starts_at, ends_at FROM staff_shift_exception
                        WHERE business_id = :businessId AND staff_id IN (:ids) AND on_date = :date""")
                .param("businessId", businessId).param("ids", ids).param("date", date)
                .query(rs -> {
                    List<WorkInterval> day = overrides.computeIfAbsent(rs.getObject("staff_id", UUID.class), k -> new ArrayList<>());
                    if ("work".equals(rs.getString("kind"))) {
                        day.add(new WorkInterval(rs.getObject("location_id", UUID.class),
                                rs.getObject("starts_at", LocalTime.class), rs.getObject("ends_at", LocalTime.class)));
                    }
                });
        Map<UUID, List<CalendarTimeOff>> timeOff = jdbc.sql("""
                        SELECT staff_id, kind, starts_at, ends_at, reason FROM staff_time_off
                        WHERE business_id = :businessId AND staff_id IN (:ids) AND :date BETWEEN starts_on AND ends_on""")
                .param("businessId", businessId).param("ids", ids).param("date", date)
                .query((rs, n) -> Map.entry(rs.getObject("staff_id", UUID.class), new CalendarTimeOff(
                        rs.getString("kind").toUpperCase(Locale.ROOT), rs.getObject("starts_at", LocalTime.class),
                        rs.getObject("ends_at", LocalTime.class), rs.getString("reason"))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        // Appointments at other branches block the stylist too, so they are shown, without customer details.
        Map<UUID, List<CalendarAppointment>> appointments = jdbc.sql("""
                        SELECT a.id, a.reference, a.status, a.start_at, a.end_at, a.blocked_until, a.staff_id, a.location_id,
                               l.name AS location_name, c.full_name,
                               a.customer_note IS NOT NULL OR a.internal_note IS NOT NULL AS has_note,
                               (SELECT string_agg(s.name, ', ' ORDER BY s.position) FROM appointment_service s
                                 WHERE s.appointment_id = a.id) AS services
                        FROM appointment a JOIN location l ON l.id = a.location_id JOIN customer c ON c.id = a.customer_id
                        WHERE a.business_id = :businessId AND a.staff_id IN (:ids) AND a.start_at < :to AND a.end_at > :from
                        ORDER BY a.start_at""")
                .param("businessId", businessId).param("ids", ids)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, n) -> {
                    boolean elsewhere = !rs.getObject("location_id", UUID.class).equals(locationId);
                    return Map.entry(rs.getObject("staff_id", UUID.class), new CalendarAppointment(
                            rs.getObject("id", UUID.class), elsewhere ? null : rs.getString("reference"),
                            status(rs.getString("status")), rs.getTimestamp("start_at").toInstant(),
                            rs.getTimestamp("end_at").toInstant(), rs.getTimestamp("blocked_until").toInstant(),
                            elsewhere ? null : rs.getString("full_name"), elsewhere ? null : rs.getString("services"),
                            !elsewhere && rs.getBoolean("has_note"), elsewhere, rs.getString("location_name")));
                })
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        List<CalendarColumn> columns = people.stream().map(p -> new CalendarColumn(p.id(), p.name(), p.online(),
                        ScheduleRules.workingOn(date, patterns.getOrDefault(p.id(), List.of()),
                                        Optional.ofNullable(overrides.get(p.id()))).stream()
                                .filter(w -> w.locationId().equals(locationId))
                                .map(w -> new LocalTime[] {w.startsAt(), w.endsAt()})
                                .toList(),
                        timeOff.getOrDefault(p.id(), List.of()), appointments.getOrDefault(p.id(), List.of())))
                .toList();
        return new DayCalendar(date, locationId, zone.getId(), opening, closure.orElse(null), closure.isPresent(), columns);
    }

    /** Seven days from the Monday of {@code anyDay}'s week, each as {@link #day} returns it (DSH-5). */
    @Transactional(readOnly = true)
    public List<DayCalendar> week(StaffPrincipal staff, UUID locationId, LocalDate anyDay) {
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return IntStream.range(0, 7).mapToObj(i -> day(staff, locationId, monday.plusDays(i))).toList();
    }

    // ---- Customers (DSH-13, DSH-14) -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CustomerMatch> searchCustomers(StaffPrincipal staff, String query) {
        if (staff.role() == StaffRole.STYLIST) {
            throw forbidden("Only reception, managers and owners can search customers.");
        }
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        String phone = Contacts.phone(q).orElse(null);
        String digits = q.replaceAll("[^0-9]", "");
        return jdbc.sql("""
                        SELECT c.id, c.full_name, c.email, c.phone_e164,
                               (SELECT max(a.start_at) FROM appointment a WHERE a.customer_id = c.id AND a.start_at < now()
                                  AND a.status IN ('checked_in', 'completed')) AS last_visit,
                               (SELECT count(*) FROM appointment a WHERE a.customer_id = c.id AND a.start_at >= now()
                                  AND a.status = 'confirmed') AS upcoming
                        FROM customer c
                        WHERE c.business_id = :businessId
                          AND (c.full_name ILIKE '%' || :q || '%'
                               OR c.email ILIKE :q || '%'
                               OR (CAST(:phone AS text) IS NOT NULL AND c.phone_e164 = :phone)
                               OR (length(:digits) >= 4 AND c.phone_e164 LIKE '%' || :digits || '%')
                               OR EXISTS (SELECT 1 FROM appointment a WHERE a.customer_id = c.id AND a.reference = upper(:q)))
                        ORDER BY similarity(c.full_name, :q) DESC, c.full_name
                        LIMIT 20""")
                .param("businessId", business()).param("q", q).param("phone", phone).param("digits", digits)
                .query((rs, n) -> new CustomerMatch(rs.getObject("id", UUID.class), rs.getString("full_name"),
                        rs.getString("email"), rs.getString("phone_e164"),
                        rs.getTimestamp("last_visit") == null ? null : rs.getTimestamp("last_visit").toInstant(),
                        rs.getInt("upcoming")))
                .list();
    }

    @Transactional(readOnly = true)
    public CustomerDetail customer(StaffPrincipal staff, UUID customerId) {
        if (staff.role() == StaffRole.STYLIST) {
            throw forbidden("Only reception, managers and owners can view customers.");
        }
        UUID businessId = business();
        List<CustomerHistoryItem> history = jdbc.sql("""
                        SELECT a.id, a.reference, a.status, a.start_at, a.location_id, l.time_zone, l.name AS location_name,
                               st.display_name AS staff_name, a.late_cancel,
                               (SELECT string_agg(s.name, ', ' ORDER BY s.position) FROM appointment_service s
                                 WHERE s.appointment_id = a.id) AS services
                        FROM appointment a JOIN location l ON l.id = a.location_id JOIN staff st ON st.id = a.staff_id
                        WHERE a.business_id = :businessId AND a.customer_id = :id
                        ORDER BY a.start_at DESC LIMIT 100""")
                .param("businessId", businessId).param("id", customerId)
                .query((rs, n) -> Map.entry(rs.getObject("location_id", UUID.class), new CustomerHistoryItem(
                        rs.getObject("id", UUID.class), rs.getString("reference"), status(rs.getString("status")),
                        rs.getTimestamp("start_at").toInstant(), rs.getString("time_zone"), rs.getString("location_name"),
                        rs.getString("staff_name"), rs.getString("services"), rs.getBoolean("late_cancel"))))
                .list().stream()
                .filter(e -> staff.canAccessLocation(e.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        return jdbc.sql("SELECT id, full_name, email, phone_e164, created_at FROM customer WHERE business_id = :businessId AND id = :id")
                .param("businessId", businessId).param("id", customerId)
                .query((rs, n) -> new CustomerDetail(customerId, rs.getString("full_name"), rs.getString("email"),
                        rs.getString("phone_e164"), rs.getTimestamp("created_at").toInstant(),
                        (int) history.stream().filter(h -> h.status() == Status.NO_SHOW).count(),
                        (int) history.stream().filter(CustomerHistoryItem::lateCancel).count(), history))
                .optional()
                .orElseThrow(() -> new NotFoundException("Customer not found."));
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static Status status(String db) {
        return Status.valueOf(db.toUpperCase(Locale.ROOT));
    }

    private static String label(Status status) {
        return switch (status) {
            case CONFIRMED -> "Confirmed";
            case CHECKED_IN -> "Checked-in";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
            case NO_SHOW -> "No-show";
        };
    }

    private static UUID business() {
        return TenantContext.requireBusinessId();
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
