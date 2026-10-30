package com.salonbooking.staff.app;

import com.salonbooking.identity.app.AdminAccess;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.staff.domain.ScheduleRules;
import com.salonbooking.staff.domain.ScheduleRules.WeeklyShift;
import com.salonbooking.staff.domain.ScheduleRules.WorkInterval;
import com.salonbooking.tenancy.TenantContext;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff schedules (STF-4..7): the weekly pattern per branch, date overrides, leave and one-off
 * breaks, and the warnings shown before a change is saved. Owners change everyone's schedule;
 * managers only the work at branches they manage, and leave of people working there.
 */
@Service
@Transactional
public class StaffScheduleService {

    public enum TimeOffKind { LEAVE, BREAK }

    /** Something worth knowing before saving; the change can still be saved. */
    public record Warning(String code, String message) {}

    public record TimeOff(UUID id, TimeOffKind kind, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt,
            LocalTime endsAt, String reason) {}

    public record TimeOffInput(TimeOffKind kind, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt,
            LocalTime endsAt, String reason) {}

    /** A date that does not follow the weekly pattern; no intervals means a day off. */
    public record DayOverride(LocalDate date, List<WorkInterval> intervals) {}

    public record Schedule(UUID staffId, String displayName, List<UUID> locationIds, List<WeeklyShift> weekly,
            List<DayOverride> overrides, List<TimeOff> timeOff) {}

    public record RotaDay(LocalDate date, boolean override, List<WorkInterval> intervals, List<TimeOff> timeOff) {}

    public record RotaRow(UUID staffId, String displayName, List<UUID> locationIds, List<RotaDay> days) {}

    public record RotaClosure(UUID locationId, LocalDate startsOn, LocalDate endsOn, String reason) {}

    /** One week, Monday to Sunday, for everyone the caller can see. */
    public record Rota(LocalDate weekStart, List<RotaRow> staff, List<RotaClosure> closures) {}

    private record StaffRef(UUID id, String displayName, List<UUID> locationIds) {}

    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("en-AU"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("en-AU"));

    private final JdbcClient jdbc;
    private final Clock clock;

    StaffScheduleService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Schedule get(StaffPrincipal principal, UUID staffId) {
        StaffRef staff = visibleStaff(principal, staffId);
        // From yesterday, so nothing disappears early for branches in time zones ahead of the server.
        LocalDate from = LocalDate.now(clock).minusDays(1);
        List<DayOverride> overrides = overrides(from, FAR_FUTURE).getOrDefault(staffId, Map.of()).entrySet().stream()
                .map(e -> new DayOverride(e.getKey(), sorted(e.getValue())))
                .sorted(Comparator.comparing(DayOverride::date))
                .toList();
        return new Schedule(staff.id(), staff.displayName(), staff.locationIds(),
                patterns().getOrDefault(staffId, List.of()), overrides,
                timeOff(from, FAR_FUTURE).getOrDefault(staffId, List.of()));
    }

    // ---- Weekly pattern -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Warning> checkWeekly(StaffPrincipal principal, UUID staffId, List<WeeklyShift> submitted) {
        StaffRef staff = visibleStaff(principal, staffId);
        List<WeeklyShift> merged = mergedPattern(principal, staff, submitted);
        Map<UUID, String> names = branchNames();
        Map<UUID, Map<Integer, List<WorkInterval>>> hours = openingHours();
        List<Warning> warnings = new ArrayList<>();
        for (WeeklyShift shift : sortedShifts(submitted)) {
            List<WorkInterval> open = hours.getOrDefault(shift.locationId(), Map.of()).getOrDefault(shift.weekday(), List.of());
            String day = DayOfWeek.of(shift.weekday()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            String where = names.get(shift.locationId());
            if (open.isEmpty()) {
                warnings.add(new Warning("BRANCH_CLOSED", "%s is closed on %ss, but %s has a shift there."
                        .formatted(where, day, staff.displayName())));
            } else if (!ScheduleRules.withinHours(shift.interval(), open)) {
                warnings.add(new Warning("OUTSIDE_HOURS", "%s %s at %s is outside opening hours (%s)."
                        .formatted(day, range(shift.interval()), where, ranges(open))));
            }
        }
        LocalDate today = LocalDate.now(clock).minusDays(1);
        Map<LocalDate, List<WorkInterval>> overrides = overrides(today, FAR_FUTURE).getOrDefault(staffId, Map.of());
        warnings.addAll(bookingsOutside(staff, upcomingBookings(staffId, today, FAR_FUTURE),
                date -> ScheduleRules.workingOn(date, merged, Optional.ofNullable(overrides.get(date))), names));
        return warnings;
    }

    public Schedule replaceWeekly(StaffPrincipal principal, UUID staffId, List<WeeklyShift> submitted) {
        StaffRef staff = visibleStaff(principal, staffId);
        mergedPattern(principal, staff, submitted);
        UUID businessId = business();
        for (WeeklyShift existing : patterns().getOrDefault(staffId, List.of())) {
            if (AdminAccess.canChangeBranch(principal, existing.locationId())) {
                jdbc.sql("""
                                DELETE FROM staff_shift WHERE business_id = :businessId AND staff_id = :staffId
                                AND location_id = :locationId""")
                        .param("businessId", businessId).param("staffId", staffId)
                        .param("locationId", existing.locationId())
                        .update();
            }
        }
        for (WeeklyShift shift : submitted) {
            jdbc.sql("""
                            INSERT INTO staff_shift (id, business_id, staff_id, location_id, weekday, starts_at, ends_at)
                            VALUES (:id, :businessId, :staffId, :locationId, :weekday, :startsAt, :endsAt)""")
                    .param("id", UUID.randomUUID()).param("businessId", businessId).param("staffId", staffId)
                    .param("locationId", shift.locationId()).param("weekday", shift.weekday())
                    .param("startsAt", shift.startsAt()).param("endsAt", shift.endsAt())
                    .update();
        }
        return get(principal, staffId);
    }

    /**
     * The pattern after saving: the submitted shifts, plus shifts at branches the caller cannot
     * change, which are kept. Rejects shifts that are impossible together.
     */
    private List<WeeklyShift> mergedPattern(StaffPrincipal principal, StaffRef staff, List<WeeklyShift> submitted) {
        for (WeeklyShift shift : submitted) {
            if (shift.weekday() < 1 || shift.weekday() > 7) {
                throw invalid("Weekdays are numbered 1 (Monday) to 7 (Sunday).");
            }
            requireWorkplace(principal, staff, shift.locationId());
        }
        List<WeeklyShift> merged = new ArrayList<>(submitted);
        patterns().getOrDefault(staff.id(), List.of()).stream()
                .filter(existing -> !AdminAccess.canChangeBranch(principal, existing.locationId()))
                .forEach(merged::add);
        for (int weekday = 1; weekday <= 7; weekday++) {
            int day = weekday;
            List<WorkInterval> intervals = merged.stream().filter(s -> s.weekday() == day).map(WeeklyShift::interval).toList();
            Optional<String> problem = ScheduleRules.problemWith(intervals);
            if (problem.isPresent()) {
                throw invalid(DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ": " + problem.get());
            }
        }
        return merged;
    }

    // ---- Date overrides -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Warning> checkDay(StaffPrincipal principal, UUID staffId, LocalDate date, List<WorkInterval> intervals) {
        StaffRef staff = visibleStaff(principal, staffId);
        validateDay(principal, staff, date, intervals);
        Map<UUID, String> names = branchNames();
        Map<UUID, Map<Integer, List<WorkInterval>>> hours = openingHours();
        List<Warning> warnings = new ArrayList<>();
        for (WorkInterval interval : sorted(intervals)) {
            String where = names.get(interval.locationId());
            Optional<String> closure = closureOn(interval.locationId(), date);
            List<WorkInterval> open = hours.getOrDefault(interval.locationId(), Map.of())
                    .getOrDefault(date.getDayOfWeek().getValue(), List.of());
            if (closure.isPresent()) {
                warnings.add(new Warning("BRANCH_CLOSED", "%s is closed on %s%s."
                        .formatted(where, DATE.format(date), closure.get().isEmpty() ? "" : " (" + closure.get() + ")")));
            } else if (open.isEmpty()) {
                warnings.add(new Warning("BRANCH_CLOSED", "%s is not open on %ss."
                        .formatted(where, date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))));
            } else if (!ScheduleRules.withinHours(interval, open)) {
                warnings.add(new Warning("OUTSIDE_HOURS", "%s at %s is outside opening hours (%s)."
                        .formatted(range(interval), where, ranges(open))));
            }
        }
        boolean onLeave = timeOff(date, date).getOrDefault(staffId, List.of()).stream()
                .anyMatch(t -> t.kind() == TimeOffKind.LEAVE);
        if (onLeave && !intervals.isEmpty()) {
            warnings.add(new Warning("ON_LEAVE", "%s is on leave on %s.".formatted(staff.displayName(), DATE.format(date))));
        }
        warnings.addAll(bookingsOutside(staff, upcomingBookings(staffId, date, date), d -> intervals, names));
        return warnings;
    }

    /** Replaces the pattern on one date; no intervals means a day off (STF-5). */
    public Schedule setDay(StaffPrincipal principal, UUID staffId, LocalDate date, List<WorkInterval> intervals) {
        StaffRef staff = visibleStaff(principal, staffId);
        validateDay(principal, staff, date, intervals);
        UUID businessId = business();
        deleteOverride(staffId, date);
        if (intervals.isEmpty()) {
            jdbc.sql("""
                            INSERT INTO staff_shift_exception (id, business_id, staff_id, on_date, kind)
                            VALUES (:id, :businessId, :staffId, :date, 'off')""")
                    .param("id", UUID.randomUUID()).param("businessId", businessId).param("staffId", staffId)
                    .param("date", date)
                    .update();
        }
        for (WorkInterval interval : intervals) {
            jdbc.sql("""
                            INSERT INTO staff_shift_exception (id, business_id, staff_id, on_date, kind, location_id,
                                                               starts_at, ends_at)
                            VALUES (:id, :businessId, :staffId, :date, 'work', :locationId, :startsAt, :endsAt)""")
                    .param("id", UUID.randomUUID()).param("businessId", businessId).param("staffId", staffId)
                    .param("date", date).param("locationId", interval.locationId())
                    .param("startsAt", interval.startsAt()).param("endsAt", interval.endsAt())
                    .update();
        }
        return get(principal, staffId);
    }

    /** Back to the weekly pattern on that date. */
    public Schedule clearDay(StaffPrincipal principal, UUID staffId, LocalDate date) {
        StaffRef staff = visibleStaff(principal, staffId);
        requireDayEditable(principal, staff, date);
        deleteOverride(staffId, date);
        return get(principal, staffId);
    }

    private void validateDay(StaffPrincipal principal, StaffRef staff, LocalDate date, List<WorkInterval> intervals) {
        requireDayEditable(principal, staff, date);
        for (WorkInterval interval : intervals) {
            requireWorkplace(principal, staff, interval.locationId());
        }
        ScheduleRules.problemWith(intervals).ifPresent(problem -> {
            throw invalid(problem);
        });
    }

    /** Managers cannot change a day that has work at a branch they do not manage. */
    private void requireDayEditable(StaffPrincipal principal, StaffRef staff, LocalDate date) {
        List<WorkInterval> current = ScheduleRules.workingOn(date, patterns().getOrDefault(staff.id(), List.of()),
                Optional.ofNullable(overrides(date, date).getOrDefault(staff.id(), Map.of()).get(date)));
        if (current.stream().anyMatch(i -> !AdminAccess.canChangeBranch(principal, i.locationId()))) {
            throw forbidden("That day includes work at a branch you don't manage.");
        }
    }

    private void deleteOverride(UUID staffId, LocalDate date) {
        jdbc.sql("DELETE FROM staff_shift_exception WHERE business_id = :businessId AND staff_id = :staffId AND on_date = :date")
                .param("businessId", business()).param("staffId", staffId).param("date", date)
                .update();
    }

    // ---- Leave and breaks -----------------------------------------------------------------------

    /** Booked appointments the leave or break would overlap (STF-7). */
    @Transactional(readOnly = true)
    public List<Warning> checkTimeOff(StaffPrincipal principal, UUID staffId, TimeOffInput input) {
        StaffRef staff = visibleStaff(principal, staffId);
        LocalDate endsOn = validTimeOffEnd(input);
        Map<UUID, String> names = branchNames();
        List<Warning> warnings = new ArrayList<>();
        for (Booked b : upcomingBookings(staffId, input.startsOn(), endsOn)) {
            boolean clash = input.kind() == TimeOffKind.LEAVE
                    || (b.from().isBefore(input.endsAt()) && input.startsAt().isBefore(b.to()));
            if (clash) {
                warnings.add(bookingWarning(staff, b, names, input.kind() == TimeOffKind.LEAVE
                        ? "during this leave" : "during this break"));
            }
        }
        return warnings;
    }

    public Schedule addTimeOff(StaffPrincipal principal, UUID staffId, TimeOffInput input) {
        visibleStaff(principal, staffId);
        LocalDate endsOn = validTimeOffEnd(input);
        boolean isBreak = input.kind() == TimeOffKind.BREAK;
        jdbc.sql("""
                        INSERT INTO staff_time_off (id, business_id, staff_id, kind, starts_on, ends_on, starts_at, ends_at, reason)
                        VALUES (:id, :businessId, :staffId, :kind, :startsOn, :endsOn, :startsAt, :endsAt, :reason)""")
                .param("id", UUID.randomUUID()).param("businessId", business()).param("staffId", staffId)
                .param("kind", input.kind().name().toLowerCase(Locale.ROOT))
                .param("startsOn", input.startsOn()).param("endsOn", endsOn)
                .param("startsAt", isBreak ? input.startsAt() : null).param("endsAt", isBreak ? input.endsAt() : null)
                .param("reason", input.reason() == null || input.reason().isBlank() ? null : input.reason().trim())
                .update();
        return get(principal, staffId);
    }

    private static LocalDate validTimeOffEnd(TimeOffInput input) {
        LocalDate endsOn = input.kind() == TimeOffKind.BREAK || input.endsOn() == null ? input.startsOn() : input.endsOn();
        if (endsOn.isBefore(input.startsOn())) {
            throw invalid("The last day of leave must be on or after the first.");
        }
        if (input.kind() == TimeOffKind.BREAK
                && (input.startsAt() == null || input.endsAt() == null || !input.startsAt().isBefore(input.endsAt()))) {
            throw invalid("A break needs a start time before its end time.");
        }
        return endsOn;
    }

    // ---- Booked appointments (STF-7) ------------------------------------------------------------

    /** An upcoming appointment in its branch's local time; {@code to} includes clean-up time. */
    private record Booked(String reference, LocalDate date, LocalTime from, LocalTime to, UUID locationId,
            String services) {}

    private List<Booked> upcomingBookings(UUID staffId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT a.reference, a.location_id, a.start_at AT TIME ZONE l.time_zone AS local_start,
                               a.blocked_until AT TIME ZONE l.time_zone AS local_end,
                               (SELECT string_agg(s.name, ', ' ORDER BY s.position) FROM appointment_service s
                                 WHERE s.appointment_id = a.id) AS services
                        FROM appointment a JOIN location l ON l.id = a.location_id
                        WHERE a.business_id = :businessId AND a.staff_id = :staffId
                          AND a.status IN ('confirmed', 'checked_in') AND a.start_at > now()
                          AND (a.start_at AT TIME ZONE l.time_zone)::date BETWEEN :from AND :to
                        ORDER BY a.start_at""")
                .param("businessId", business()).param("staffId", staffId).param("from", from).param("to", to)
                .query((rs, row) -> {
                    LocalDateTime start = rs.getObject("local_start", LocalDateTime.class);
                    LocalDateTime end = rs.getObject("local_end", LocalDateTime.class);
                    // Past midnight cannot fit any shift; LocalTime.MAX makes that explicit.
                    LocalTime until = end.toLocalDate().equals(start.toLocalDate()) ? end.toLocalTime() : LocalTime.MAX;
                    return new Booked(rs.getString("reference"), start.toLocalDate(), start.toLocalTime(), until,
                            rs.getObject("location_id", UUID.class), rs.getString("services"));
                })
                .list();
    }

    /** Appointments that would no longer fall inside a shift at their branch. */
    private List<Warning> bookingsOutside(StaffRef staff, List<Booked> booked,
            Function<LocalDate, List<WorkInterval>> workOn, Map<UUID, String> names) {
        List<Warning> warnings = new ArrayList<>();
        for (Booked b : booked) {
            boolean fits = workOn.apply(b.date()).stream().anyMatch(w -> w.locationId().equals(b.locationId())
                    && !b.from().isBefore(w.startsAt()) && !b.to().isAfter(w.endsAt()));
            if (!fits) {
                warnings.add(bookingWarning(staff, b, names, "outside their hours"));
            }
        }
        return warnings;
    }

    private static Warning bookingWarning(StaffRef staff, Booked b, Map<UUID, String> names, String problem) {
        return new Warning("BOOKED_APPOINTMENT", "%s has a booking on %s at %s at %s (%s, ref %s) that would be %s. Contact the customer to rebook."
                .formatted(staff.displayName(), DATE.format(b.date()), TIME.format(b.from()), names.get(b.locationId()),
                        b.services(), b.reference(), problem));
    }

    public Schedule removeTimeOff(StaffPrincipal principal, UUID staffId, UUID timeOffId) {
        visibleStaff(principal, staffId);
        int removed = jdbc.sql("DELETE FROM staff_time_off WHERE business_id = :businessId AND staff_id = :staffId AND id = :id")
                .param("businessId", business()).param("staffId", staffId).param("id", timeOffId)
                .update();
        if (removed == 0) {
            throw new NotFoundException("Leave or break not found.");
        }
        return get(principal, staffId);
    }

    // ---- Rota -----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Rota week(StaffPrincipal principal, LocalDate anyDay, UUID locationId) {
        if (locationId != null && (!principal.canAccessLocation(locationId) || !branchNames().containsKey(locationId))) {
            throw new NotFoundException("Branch not found.");
        }
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        List<LocalDate> dates = IntStream.range(0, 7).mapToObj(monday::plusDays).toList();

        Map<UUID, List<WeeklyShift>> patterns = patterns();
        Map<UUID, Map<LocalDate, List<WorkInterval>>> overrides = overrides(monday, sunday);
        Map<UUID, List<TimeOff>> timeOff = timeOff(monday, sunday);

        List<RotaRow> rows = allStaff(true).stream()
                .filter(s -> locationId == null ? isVisible(principal, s) : s.locationIds().contains(locationId))
                .map(s -> new RotaRow(s.id(), s.displayName(), s.locationIds(), dates.stream()
                        .map(date -> {
                            Optional<List<WorkInterval>> override =
                                    Optional.ofNullable(overrides.getOrDefault(s.id(), Map.of()).get(date));
                            return new RotaDay(date, override.isPresent(),
                                    ScheduleRules.workingOn(date, patterns.getOrDefault(s.id(), List.of()), override),
                                    timeOff.getOrDefault(s.id(), List.of()).stream()
                                            .filter(t -> !date.isBefore(t.startsOn()) && !date.isAfter(t.endsOn()))
                                            .toList());
                        })
                        .toList()))
                .toList();

        List<RotaClosure> closures = jdbc.sql("""
                        SELECT location_id, starts_on, ends_on, reason FROM location_closure
                        WHERE business_id = :businessId AND starts_on <= :sunday AND ends_on >= :monday
                        ORDER BY starts_on""")
                .param("businessId", business()).param("monday", monday).param("sunday", sunday)
                .query((rs, row) -> new RotaClosure(rs.getObject("location_id", UUID.class),
                        rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                        rs.getString("reason")))
                .list().stream()
                .filter(c -> principal.canAccessLocation(c.locationId()))
                .toList();
        return new Rota(monday, rows, closures);
    }

    // ---- Access ---------------------------------------------------------------------------------

    /** Owners see everyone; managers see people who work at one of their branches. Others are "not found". */
    private StaffRef visibleStaff(StaffPrincipal principal, UUID staffId) {
        return allStaff(false).stream()
                .filter(s -> s.id().equals(staffId) && isVisible(principal, s))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Staff member not found."));
    }

    private static boolean isVisible(StaffPrincipal principal, StaffRef staff) {
        return principal.role() == StaffRole.OWNER || staff.locationIds().stream().anyMatch(principal::canAccessLocation);
    }

    private static void requireWorkplace(StaffPrincipal principal, StaffRef staff, UUID locationId) {
        if (!staff.locationIds().contains(locationId)) {
            throw invalid("%s doesn't work at that branch. Add the branch to their profile first."
                    .formatted(staff.displayName()));
        }
        AdminAccess.requireBranch(principal, locationId);
    }

    // ---- Loading --------------------------------------------------------------------------------

    private List<StaffRef> allStaff(boolean activeOnly) {
        UUID businessId = business();
        Map<UUID, List<UUID>> links = jdbc.sql("SELECT staff_id, location_id FROM staff_location WHERE business_id = :businessId")
                .param("businessId", businessId)
                .query((rs, row) -> Map.entry(rs.getObject("staff_id", UUID.class), rs.getObject("location_id", UUID.class)))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return jdbc.sql("""
                        SELECT id, display_name FROM staff
                        WHERE business_id = :businessId AND (active OR NOT :activeOnly)
                        ORDER BY display_name""")
                .param("businessId", businessId).param("activeOnly", activeOnly)
                .query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new StaffRef(id, rs.getString("display_name"), links.getOrDefault(id, List.of()));
                })
                .list();
    }

    private Map<UUID, List<WeeklyShift>> patterns() {
        record Row(UUID staffId, WeeklyShift shift) {}
        return jdbc.sql("""
                        SELECT staff_id, weekday, location_id, starts_at, ends_at FROM staff_shift
                        WHERE business_id = :businessId ORDER BY weekday, starts_at""")
                .param("businessId", business())
                .query((rs, row) -> new Row(rs.getObject("staff_id", UUID.class), new WeeklyShift(rs.getInt("weekday"),
                        rs.getObject("location_id", UUID.class), rs.getObject("starts_at", LocalTime.class),
                        rs.getObject("ends_at", LocalTime.class))))
                .list().stream()
                .collect(Collectors.groupingBy(Row::staffId, Collectors.mapping(Row::shift, Collectors.toList())));
    }

    /** Overrides by staff and date; a day off is an empty list. */
    private Map<UUID, Map<LocalDate, List<WorkInterval>>> overrides(LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, List<WorkInterval>>> result = new HashMap<>();
        jdbc.sql("""
                        SELECT staff_id, on_date, kind, location_id, starts_at, ends_at FROM staff_shift_exception
                        WHERE business_id = :businessId AND on_date BETWEEN :from AND :to""")
                .param("businessId", business()).param("from", from).param("to", to)
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

    /** Leave and breaks that touch the date range, by staff, in date order. */
    private Map<UUID, List<TimeOff>> timeOff(LocalDate from, LocalDate to) {
        record Row(UUID staffId, TimeOff timeOff) {}
        return jdbc.sql("""
                        SELECT id, staff_id, kind, starts_on, ends_on, starts_at, ends_at, reason FROM staff_time_off
                        WHERE business_id = :businessId AND ends_on >= :from AND starts_on <= :to
                        ORDER BY starts_on, starts_at NULLS FIRST""")
                .param("businessId", business()).param("from", from).param("to", to)
                .query((rs, row) -> new Row(rs.getObject("staff_id", UUID.class), new TimeOff(rs.getObject("id", UUID.class),
                        TimeOffKind.valueOf(rs.getString("kind").toUpperCase(Locale.ROOT)),
                        rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
                        rs.getObject("starts_at", LocalTime.class), rs.getObject("ends_at", LocalTime.class),
                        rs.getString("reason"))))
                .list().stream()
                .collect(Collectors.groupingBy(Row::staffId, Collectors.mapping(Row::timeOff, Collectors.toList())));
    }

    private Map<UUID, String> branchNames() {
        return jdbc.sql("SELECT id, name FROM location WHERE business_id = :businessId")
                .param("businessId", business())
                .query((rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("name")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Opening ranges by branch and weekday. */
    private Map<UUID, Map<Integer, List<WorkInterval>>> openingHours() {
        record Row(int weekday, WorkInterval range) {}
        return jdbc.sql("""
                        SELECT location_id, weekday, opens_at, closes_at FROM location_hours
                        WHERE business_id = :businessId ORDER BY opens_at""")
                .param("businessId", business())
                .query((rs, row) -> new Row(rs.getInt("weekday"), new WorkInterval(rs.getObject("location_id", UUID.class),
                        rs.getObject("opens_at", LocalTime.class), rs.getObject("closes_at", LocalTime.class))))
                .list().stream()
                .collect(Collectors.groupingBy(r -> r.range().locationId(),
                        Collectors.groupingBy(Row::weekday, Collectors.mapping(Row::range, Collectors.toList()))));
    }

    /** The reason of a closure covering the date (empty if it has none), or nothing if the branch is open. */
    private Optional<String> closureOn(UUID locationId, LocalDate date) {
        return jdbc.sql("""
                        SELECT coalesce(reason, '') FROM location_closure
                        WHERE business_id = :businessId AND location_id = :locationId AND :date BETWEEN starts_on AND ends_on
                        LIMIT 1""")
                .param("businessId", business()).param("locationId", locationId).param("date", date)
                .query(String.class)
                .optional();
    }

    private static List<WorkInterval> sorted(List<WorkInterval> intervals) {
        return intervals.stream().sorted(Comparator.comparing(WorkInterval::startsAt)).toList();
    }

    private static List<WeeklyShift> sortedShifts(List<WeeklyShift> shifts) {
        return shifts.stream()
                .sorted(Comparator.comparingInt(WeeklyShift::weekday).thenComparing(WeeklyShift::startsAt))
                .toList();
    }

    private static String range(WorkInterval interval) {
        return TIME.format(interval.startsAt()) + "–" + TIME.format(interval.endsAt());
    }

    private static String ranges(List<WorkInterval> intervals) {
        return intervals.stream().map(StaffScheduleService::range).collect(Collectors.joining(", "));
    }

    private static UUID business() {
        return TenantContext.requireBusinessId();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
