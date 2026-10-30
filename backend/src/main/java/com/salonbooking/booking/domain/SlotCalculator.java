package com.salonbooking.booking.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Available start times for one branch on one date (AVL-1..5, AVL-7). A pure function: callers
 * load the inputs, and nothing here touches Spring or the database.
 *
 * <p>A start is offered for a stylist when the whole appointment, clean-up included, fits inside
 * one opening range, inside one of their working intervals at this branch, and clear of every busy
 * range (breaks, leave, other appointments at any branch).
 */
public final class SlotCalculator {

    /** Local times on the date; an empty list means the branch is closed. */
    public record LocalRange(LocalTime from, LocalTime to) {}

    /** A busy stretch in absolute time. */
    public record BusyRange(Instant from, Instant to) {}

    /** A qualified stylist: where they work at this branch on the date, and when they are busy. */
    public record Candidate(UUID staffId, List<LocalRange> work, List<BusyRange> busy) {}

    /**
     * @param blockedMin appointment length plus clean-up after the last service
     * @param earliest no start before this (now + minimum notice)
     * @param latest no start at or after this (end of the booking horizon)
     */
    public record Input(LocalDate date, ZoneId zone, int slotIntervalMin, List<LocalRange> opening, int blockedMin,
            Instant earliest, Instant latest, List<Candidate> candidates) {}

    /** A bookable start and the stylists free for it. */
    public record Slot(Instant start, LocalTime localTime, List<UUID> staffIds) {}

    private SlotCalculator() {}

    public static List<Slot> compute(Input in) {
        List<Slot> slots = new ArrayList<>();
        for (LocalRange open : in.opening()) {
            Instant closes = instant(in.date(), open.to(), in.zone());
            for (LocalTime t = open.from(); t.isBefore(open.to()); t = t.plusMinutes(in.slotIntervalMin())) {
                LocalDateTime local = LocalDateTime.of(in.date(), t);
                ZonedDateTime zoned = ZonedDateTime.of(local, in.zone());
                if (!zoned.toLocalDateTime().equals(local)) {
                    continue; // does not exist: clocks jump forward over it
                }
                Instant start = zoned.toInstant();
                Instant end = start.plusSeconds(in.blockedMin() * 60L);
                if (start.isBefore(in.earliest()) || !start.isBefore(in.latest()) || end.isAfter(closes)) {
                    continue;
                }
                List<UUID> free = in.candidates().stream()
                        .filter(c -> isFree(c, in.date(), in.zone(), start, end))
                        .map(Candidate::staffId)
                        .toList();
                if (!free.isEmpty()) {
                    slots.add(new Slot(start, t, free));
                }
                if (t.plusMinutes(in.slotIntervalMin()).isBefore(t)) {
                    break; // wrapped past midnight
                }
            }
        }
        return slots;
    }

    private static boolean isFree(Candidate c, LocalDate date, ZoneId zone, Instant start, Instant end) {
        boolean working = c.work().stream().anyMatch(w ->
                !start.isBefore(instant(date, w.from(), zone)) && !end.isAfter(instant(date, w.to(), zone)));
        return working && c.busy().stream().noneMatch(b -> start.isBefore(b.to()) && b.from().isBefore(end));
    }

    /** A local time on the date as an instant; times in a daylight-saving gap move forward. */
    public static Instant instant(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }
}
