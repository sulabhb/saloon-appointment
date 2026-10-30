package com.salonbooking.booking.app;

import com.salonbooking.booking.domain.SlotCalculator;
import com.salonbooking.booking.domain.SlotCalculator.BusyRange;
import com.salonbooking.booking.domain.SlotCalculator.Candidate;
import com.salonbooking.booking.domain.SlotCalculator.LocalRange;
import com.salonbooking.booking.domain.SlotCalculator.Slot;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.staff.domain.ScheduleRules;
import com.salonbooking.staff.domain.ScheduleRules.WeeklyShift;
import com.salonbooking.staff.domain.ScheduleRules.WorkInterval;
import com.salonbooking.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads everything availability depends on and runs {@link SlotCalculator} (HLD 8.1). Used by the
 * public booking pages and, inside the booking transaction, to re-check a chosen time (BKG-9).
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    /** A service as offered at the branch: branch overrides applied. */
    public record ResolvedService(UUID id, String name, int durationMin, int bufferAfterMin, long priceCents,
            boolean priceIsFrom) {}

    /** The branch and its booking policy, business defaults filled in. */
    public record Branch(UUID id, String slug, String name, ZoneId zone, int slotIntervalMin, int minNoticeMin,
            int horizonDays) {}

    /**
     * What a request is about: where, what, and who may do it (AVL-3). {@code ignoredAppointment}
     * is left out of busy times, so an appointment being rescheduled does not block itself.
     */
    public record Request(Branch branch, List<ResolvedService> services, List<UUID> candidates, int durationMin,
            int blockedMin, UUID ignoredAppointment) {

        public long totalPriceCents() {
            return services.stream().mapToLong(ResolvedService::priceCents).sum();
        }

        public Request ignoring(UUID appointmentId) {
            return new Request(branch, services, candidates, durationMin, blockedMin, appointmentId);
        }
    }

    /** Staff can book any time from now on up to a year ahead, including stylists not bookable online. */
    private static final int STAFF_HORIZON_DAYS = 365;

    public enum DayStatus { AVAILABLE, FULL, CLOSED }

    private final JdbcClient jdbc;
    private final Clock clock;

    AvailabilityService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Resolves a request. {@code staffId} null means any stylist. Fails with NO_QUALIFIED_STYLIST
     * when nobody can do every chosen service (AVL-8).
     */
    public Request request(String locationSlug, List<UUID> serviceIds, UUID staffId) {
        return resolve(branch("l.slug = :key", locationSlug, false), serviceIds, staffId, false);
    }

    /** The same for staff booking at a branch they can access (DSH-9, STF-8). */
    public Request staffRequest(UUID locationId, List<UUID> serviceIds, UUID staffId) {
        return resolve(branch("l.id = :key", locationId, true), serviceIds, staffId, true);
    }

    private Branch branch(String condition, Object key, boolean staffBooking) {
        return jdbc.sql("""
                        SELECT l.id, l.slug, l.name, l.time_zone,
                               coalesce(l.slot_interval_min, b.slot_interval_min) AS slot_interval_min,
                               coalesce(l.min_notice_min, b.min_notice_min) AS min_notice_min,
                               coalesce(l.horizon_days, b.horizon_days) AS horizon_days
                        FROM location l JOIN business b ON b.id = l.business_id
                        WHERE l.business_id = :businessId AND l.active AND\s""" + condition)
                .param("businessId", business()).param("key", key)
                .query((rs, row) -> new Branch(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                        ZoneId.of(rs.getString("time_zone")), rs.getInt("slot_interval_min"),
                        staffBooking ? 0 : rs.getInt("min_notice_min"),
                        staffBooking ? STAFF_HORIZON_DAYS : rs.getInt("horizon_days")))
                .optional()
                .orElseThrow(() -> new NotFoundException("Branch not found."));
    }

    private Request resolve(Branch branch, List<UUID> serviceIds, UUID staffId, boolean staffBooking) {
        UUID businessId = business();
        List<UUID> distinct = serviceIds.stream().distinct().toList();
        if (distinct.isEmpty() || distinct.size() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Choose between 1 and 10 services.");
        }
        Map<UUID, ResolvedService> offered = jdbc.sql("""
                        SELECT s.id, s.name, coalesce(ls.duration_min, s.default_duration_min) AS duration_min,
                               s.buffer_after_min, coalesce(ls.price, s.default_price) AS price, s.price_is_from
                        FROM service s JOIN location_service ls ON ls.service_id = s.id AND ls.location_id = :locationId
                        WHERE s.business_id = :businessId AND s.active AND s.id IN (:ids)""")
                .param("businessId", businessId).param("locationId", branch.id()).param("ids", distinct)
                .query((rs, row) -> new ResolvedService(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getInt("duration_min"), rs.getInt("buffer_after_min"), rs.getLong("price"),
                        rs.getBoolean("price_is_from")))
                .list().stream()
                .collect(Collectors.toMap(ResolvedService::id, s -> s));
        if (offered.size() != distinct.size()) {
            throw new NotFoundException("One of those services is not offered at this branch.");
        }
        List<ResolvedService> services = distinct.stream().map(offered::get).toList();

        List<UUID> qualified = jdbc.sql("""
                        SELECT st.id FROM staff st
                        JOIN staff_location sl ON sl.staff_id = st.id AND sl.location_id = :locationId
                        WHERE st.business_id = :businessId AND st.active AND (st.bookable_online OR :staffBooking)
                          AND (SELECT count(*) FROM staff_service ss WHERE ss.staff_id = st.id AND ss.service_id IN (:ids)) = :count
                        ORDER BY st.display_name""")
                .param("businessId", businessId).param("locationId", branch.id()).param("ids", distinct)
                .param("count", distinct.size()).param("staffBooking", staffBooking)
                .query(UUID.class)
                .list();
        List<UUID> candidates = staffId == null ? qualified : qualified.stream().filter(staffId::equals).toList();
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_QUALIFIED_STYLIST", staffId == null
                    ? "No one at this branch does all of those services in one visit. Remove a service or contact the branch."
                    : "That stylist doesn't do all of those services at this branch.");
        }

        int duration = services.stream().mapToInt(ResolvedService::durationMin).sum();
        int blocked = duration + services.getLast().bufferAfterMin();
        return new Request(branch, services, candidates, duration, blocked, null);
    }

    /** Bookable starts on one date (AVL-1). */
    public List<Slot> slots(Request request, LocalDate date) {
        return compute(request, date, date).getOrDefault(date, List.of());
    }

    /** Per-date summary for a date picker (AVL-6). */
    public Map<LocalDate, DayStatus> days(Request request, LocalDate from, LocalDate to) {
        Map<LocalDate, List<Slot>> slots = compute(request, from, to);
        Map<LocalDate, List<LocalRange>> opening = opening(request.branch(), from, to);
        Map<LocalDate, DayStatus> result = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.put(d, opening.getOrDefault(d, List.of()).isEmpty() ? DayStatus.CLOSED
                    : slots.getOrDefault(d, List.of()).isEmpty() ? DayStatus.FULL : DayStatus.AVAILABLE);
        }
        return result;
    }

    /** Stylists free for exactly this start, in the order to try them (BKG-9, BKG-13). */
    public List<UUID> freeAt(Request request, Instant start) {
        LocalDate date = start.atZone(request.branch().zone()).toLocalDate();
        return slots(request, date).stream()
                .filter(s -> s.start().equals(start))
                .findFirst()
                .map(Slot::staffIds)
                .orElse(List.of());
    }

    /** The last date the booking horizon allows, in the branch's time zone (AVL-5). */
    public LocalDate lastBookableDate(Branch branch) {
        return LocalDate.now(clock.withZone(branch.zone())).plusDays(branch.horizonDays());
    }

    private Map<LocalDate, List<Slot>> compute(Request request, LocalDate from, LocalDate to) {
        Branch branch = request.branch();
        ZoneId zone = branch.zone();
        Instant earliest = clock.instant().plusSeconds(branch.minNoticeMin() * 60L);
        Instant latest = SlotCalculator.instant(lastBookableDate(branch).plusDays(1), LocalTime.MIDNIGHT, zone);
        List<UUID> staff = request.candidates();

        Map<LocalDate, List<LocalRange>> opening = opening(branch, from, to);
        Map<UUID, List<WeeklyShift>> patterns = patterns(staff);
        Map<UUID, Map<LocalDate, List<WorkInterval>>> overrides = overrides(staff, from, to);
        Map<UUID, List<TimeOff>> timeOff = timeOff(staff, from, to);
        // Appointments anywhere in the business block the stylist (AVL-2); a day either side covers time zones.
        Map<UUID, List<BusyRange>> appointments = appointments(staff, request.ignoredAppointment(),
                SlotCalculator.instant(from.minusDays(1), LocalTime.MIDNIGHT, zone),
                SlotCalculator.instant(to.plusDays(2), LocalTime.MIDNIGHT, zone));

        Map<LocalDate, List<Slot>> result = new HashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            LocalDate day = date;
            List<LocalRange> open = opening.getOrDefault(day, List.of());
            if (open.isEmpty()) {
                continue;
            }
            List<Candidate> candidates = staff.stream().map(id -> {
                List<LocalRange> work = ScheduleRules.workingOn(day, patterns.getOrDefault(id, List.of()),
                                Optional.ofNullable(overrides.getOrDefault(id, Map.of()).get(day))).stream()
                        .filter(w -> w.locationId().equals(branch.id()))
                        .map(w -> new LocalRange(w.startsAt(), w.endsAt()))
                        .toList();
                List<BusyRange> busy = new ArrayList<>(appointments.getOrDefault(id, List.of()));
                for (TimeOff t : timeOff.getOrDefault(id, List.of())) {
                    if (!day.isBefore(t.startsOn()) && !day.isAfter(t.endsOn())) {
                        busy.add(t.startsAt() == null
                                ? new BusyRange(SlotCalculator.instant(day, LocalTime.MIDNIGHT, zone),
                                        SlotCalculator.instant(day.plusDays(1), LocalTime.MIDNIGHT, zone))
                                : new BusyRange(SlotCalculator.instant(day, t.startsAt(), zone),
                                        SlotCalculator.instant(day, t.endsAt(), zone)));
                    }
                }
                return new Candidate(id, work, busy);
            }).toList();
            result.put(day, SlotCalculator.compute(new SlotCalculator.Input(day, zone, branch.slotIntervalMin(), open,
                    request.blockedMin(), earliest, latest, candidates)));
        }
        return result;
    }

    // ---- Loading --------------------------------------------------------------------------------

    private record TimeOff(LocalDate startsOn, LocalDate endsOn, LocalTime startsAt, LocalTime endsAt) {}

    /** Opening ranges per date; closed days and closures have none. */
    private Map<LocalDate, List<LocalRange>> opening(Branch branch, LocalDate from, LocalDate to) {
        UUID businessId = business();
        Map<Integer, List<LocalRange>> weekly = jdbc.sql("""
                        SELECT weekday, opens_at, closes_at FROM location_hours
                        WHERE business_id = :businessId AND location_id = :id ORDER BY opens_at""")
                .param("businessId", businessId).param("id", branch.id())
                .query((rs, row) -> Map.entry(rs.getInt("weekday"),
                        new LocalRange(rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        List<LocalDate[]> closures = jdbc.sql("""
                        SELECT starts_on, ends_on FROM location_closure
                        WHERE business_id = :businessId AND location_id = :id AND starts_on <= :to AND ends_on >= :from""")
                .param("businessId", businessId).param("id", branch.id()).param("from", from).param("to", to)
                .query((rs, row) -> new LocalDate[] {rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class)})
                .list();
        Map<LocalDate, List<LocalRange>> result = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDate day = d;
            boolean closed = closures.stream().anyMatch(c -> !day.isBefore(c[0]) && !day.isAfter(c[1]));
            result.put(day, closed ? List.of() : weekly.getOrDefault(day.getDayOfWeek().getValue(), List.of()));
        }
        return result;
    }

    private Map<UUID, List<WeeklyShift>> patterns(List<UUID> staff) {
        record Row(UUID staffId, WeeklyShift shift) {}
        return jdbc.sql("""
                        SELECT staff_id, weekday, location_id, starts_at, ends_at FROM staff_shift
                        WHERE business_id = :businessId AND staff_id IN (:staff)""")
                .param("businessId", business()).param("staff", staff)
                .query((rs, row) -> new Row(rs.getObject("staff_id", UUID.class), new WeeklyShift(rs.getInt("weekday"),
                        rs.getObject("location_id", UUID.class), rs.getObject("starts_at", LocalTime.class),
                        rs.getObject("ends_at", LocalTime.class))))
                .list().stream()
                .collect(Collectors.groupingBy(Row::staffId, Collectors.mapping(Row::shift, Collectors.toList())));
    }

    private Map<UUID, Map<LocalDate, List<WorkInterval>>> overrides(List<UUID> staff, LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, List<WorkInterval>>> result = new HashMap<>();
        jdbc.sql("""
                        SELECT staff_id, on_date, kind, location_id, starts_at, ends_at FROM staff_shift_exception
                        WHERE business_id = :businessId AND staff_id IN (:staff) AND on_date BETWEEN :from AND :to""")
                .param("businessId", business()).param("staff", staff).param("from", from).param("to", to)
                .query(rs -> {
                    List<WorkInterval> day = result
                            .computeIfAbsent(rs.getObject("staff_id", UUID.class), k -> new HashMap<>())
                            .computeIfAbsent(rs.getObject("on_date", LocalDate.class), k -> new ArrayList<>());
                    if ("work".equals(rs.getString("kind"))) {
                        day.add(new WorkInterval(rs.getObject("location_id", UUID.class),
                                rs.getObject("starts_at", LocalTime.class), rs.getObject("ends_at", LocalTime.class)));
                    }
                });
        return result;
    }

    private Map<UUID, List<TimeOff>> timeOff(List<UUID> staff, LocalDate from, LocalDate to) {
        record Row(UUID staffId, TimeOff timeOff) {}
        return jdbc.sql("""
                        SELECT staff_id, starts_on, ends_on, starts_at, ends_at FROM staff_time_off
                        WHERE business_id = :businessId AND staff_id IN (:staff) AND ends_on >= :from AND starts_on <= :to""")
                .param("businessId", business()).param("staff", staff).param("from", from).param("to", to)
                .query((rs, row) -> new Row(rs.getObject("staff_id", UUID.class), new TimeOff(
                        rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                        rs.getObject("starts_at", LocalTime.class), rs.getObject("ends_at", LocalTime.class))))
                .list().stream()
                .collect(Collectors.groupingBy(Row::staffId, Collectors.mapping(Row::timeOff, Collectors.toList())));
    }

    private Map<UUID, List<BusyRange>> appointments(List<UUID> staff, UUID ignored, Instant from, Instant to) {
        record Row(UUID staffId, BusyRange range) {}
        return jdbc.sql("""
                        SELECT staff_id, start_at, blocked_until FROM appointment
                        WHERE business_id = :businessId AND staff_id IN (:staff)
                          AND status IN ('confirmed', 'checked_in', 'completed')
                          AND start_at < :to AND blocked_until > :from
                          AND id IS DISTINCT FROM CAST(:ignored AS uuid)""")
                .param("businessId", business()).param("staff", staff).param("ignored", ignored)
                .param("from", java.sql.Timestamp.from(from)).param("to", java.sql.Timestamp.from(to))
                .query((rs, row) -> new Row(rs.getObject("staff_id", UUID.class), new BusyRange(
                        rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("blocked_until").toInstant())))
                .list().stream()
                .collect(Collectors.groupingBy(Row::staffId, Collectors.mapping(Row::range, Collectors.toList())));
    }

    private static UUID business() {
        return TenantContext.requireBusinessId();
    }
}
