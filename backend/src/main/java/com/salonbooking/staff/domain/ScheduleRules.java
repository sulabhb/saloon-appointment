package com.salonbooking.staff.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rules for staff schedules (STF-4..6), free of Spring and the database so the availability
 * engine can use them too. All times are local to the branch.
 */
public final class ScheduleRules {

    /** A stretch of work at one branch. */
    public record WorkInterval(UUID locationId, LocalTime startsAt, LocalTime endsAt) {}

    /** Part of the weekly pattern; weekday is ISO (1 = Monday). */
    public record WeeklyShift(int weekday, UUID locationId, LocalTime startsAt, LocalTime endsAt) {

        public WorkInterval interval() {
            return new WorkInterval(locationId, startsAt, endsAt);
        }
    }

    private ScheduleRules() {}

    /**
     * The intervals someone works on a date: the override for that date if there is one (empty
     * means a day off), otherwise the weekly pattern for that weekday. Sorted by start time.
     */
    public static List<WorkInterval> workingOn(LocalDate date, List<WeeklyShift> pattern,
            Optional<List<WorkInterval>> override) {
        List<WorkInterval> intervals = override.orElseGet(() -> pattern.stream()
                .filter(s -> s.weekday() == date.getDayOfWeek().getValue())
                .map(WeeklyShift::interval)
                .toList());
        return intervals.stream().sorted(Comparator.comparing(WorkInterval::startsAt)).toList();
    }

    /** Problems that make a day's intervals impossible, or empty if they are fine. */
    public static Optional<String> problemWith(List<WorkInterval> day) {
        for (WorkInterval interval : day) {
            if (!interval.startsAt().isBefore(interval.endsAt())) {
                return Optional.of("Each start time must be before its end time.");
            }
        }
        List<WorkInterval> sorted = day.stream().sorted(Comparator.comparing(WorkInterval::startsAt)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).startsAt().isBefore(sorted.get(i - 1).endsAt())) {
                return Optional.of(sorted.get(i).locationId().equals(sorted.get(i - 1).locationId())
                        ? "Shifts on the same day must not overlap."
                        : "Someone can't work at two branches at the same time.");
            }
        }
        return Optional.empty();
    }

    /** Whether an interval lies entirely inside one of the opening ranges. */
    public static boolean withinHours(WorkInterval interval, List<WorkInterval> openingRanges) {
        return openingRanges.stream().anyMatch(open -> !interval.startsAt().isBefore(open.startsAt())
                && !interval.endsAt().isAfter(open.endsAt()));
    }
}
