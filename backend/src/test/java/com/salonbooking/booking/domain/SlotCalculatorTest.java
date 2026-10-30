package com.salonbooking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.salonbooking.booking.domain.SlotCalculator.BusyRange;
import com.salonbooking.booking.domain.SlotCalculator.Candidate;
import com.salonbooking.booking.domain.SlotCalculator.Input;
import com.salonbooking.booking.domain.SlotCalculator.LocalRange;
import com.salonbooking.booking.domain.SlotCalculator.Slot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlotCalculatorTest {

    static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    static final ZoneId BRISBANE = ZoneId.of("Australia/Brisbane");
    static final LocalDate DAY = LocalDate.of(2030, 6, 3);
    static final UUID SAM = UUID.randomUUID();
    static final UUID KIM = UUID.randomUUID();

    static LocalRange range(String from, String to) {
        return new LocalRange(LocalTime.parse(from), LocalTime.parse(to));
    }

    static BusyRange busy(LocalDate date, String from, String to, ZoneId zone) {
        return new BusyRange(SlotCalculator.instant(date, LocalTime.parse(from), zone),
                SlotCalculator.instant(date, LocalTime.parse(to), zone));
    }

    static Input input(int blockedMin, List<Candidate> candidates) {
        return new Input(DAY, SYDNEY, 30, List.of(range("09:00", "12:00")), blockedMin, Instant.MIN, Instant.MAX,
                candidates);
    }

    static List<String> times(List<Slot> slots) {
        return slots.stream().map(s -> s.localTime().toString()).toList();
    }

    @Test
    void theWholeAppointmentIncludingCleanUpMustFitBeforeClosing() {
        Candidate sam = new Candidate(SAM, List.of(range("09:00", "12:00")), List.of());
        assertThat(times(SlotCalculator.compute(input(60, List.of(sam)))))
                .containsExactly("09:00", "09:30", "10:00", "10:30", "11:00");
        assertThat(times(SlotCalculator.compute(input(75, List.of(sam)))))
                .containsExactly("09:00", "09:30", "10:00", "10:30");
    }

    @Test
    void onlyWorkingTimeAtThisBranchCounts() {
        Candidate sam = new Candidate(SAM, List.of(range("10:00", "11:30")), List.of());
        assertThat(times(SlotCalculator.compute(input(60, List.of(sam))))).containsExactly("10:00", "10:30");
    }

    @Test
    void breaksLeaveAndOtherAppointmentsBlockTime() {
        Candidate sam = new Candidate(SAM, List.of(range("09:00", "12:00")),
                List.of(busy(DAY, "10:00", "10:30", SYDNEY)));
        // 09:30 would run into the break; back-to-back after it is fine.
        assertThat(times(SlotCalculator.compute(input(30, List.of(sam)))))
                .containsExactly("09:00", "09:30", "10:30", "11:00", "11:30");
        assertThat(times(SlotCalculator.compute(input(60, List.of(sam))))).containsExactly("09:00", "10:30", "11:00");
    }

    @Test
    void anyStylistOffersATimeWhenOneIsFreeAndListsWhoIs() {
        Candidate sam = new Candidate(SAM, List.of(range("09:00", "10:00")), List.of());
        Candidate kim = new Candidate(KIM, List.of(range("09:30", "12:00")), List.of());
        List<Slot> slots = SlotCalculator.compute(input(30, List.of(sam, kim)));
        assertThat(times(slots)).containsExactly("09:00", "09:30", "10:00", "10:30", "11:00", "11:30");
        assertThat(slots.get(1).staffIds()).containsExactly(SAM, KIM);
        assertThat(slots.get(2).staffIds()).containsExactly(KIM);
    }

    @Test
    void minimumNoticeAndHorizonCutTheDay() {
        Candidate sam = new Candidate(SAM, List.of(range("09:00", "12:00")), List.of());
        Input in = new Input(DAY, SYDNEY, 30, List.of(range("09:00", "12:00")), 30,
                SlotCalculator.instant(DAY, LocalTime.of(10, 0), SYDNEY),
                SlotCalculator.instant(DAY, LocalTime.of(11, 0), SYDNEY), List.of(sam));
        assertThat(times(SlotCalculator.compute(in))).containsExactly("10:00", "10:30");
    }

    @Test
    void closedDaysAndUnavailableStylistsGiveNothing() {
        assertThat(SlotCalculator.compute(new Input(DAY, SYDNEY, 30, List.of(), 30, Instant.MIN, Instant.MAX,
                List.of(new Candidate(SAM, List.of(range("09:00", "12:00")), List.of()))))).isEmpty();
        assertThat(SlotCalculator.compute(input(30, List.of(new Candidate(SAM, List.of(), List.of()))))).isEmpty();
    }

    @Test
    void timesThatDoNotExistWhenClocksGoForwardAreSkipped() {
        // Sydney: 4 October 2026, 02:00 jumps to 03:00.
        LocalDate dstStart = LocalDate.of(2026, 10, 4);
        Candidate sam = new Candidate(SAM, List.of(range("01:00", "05:00")), List.of());
        Input in = new Input(dstStart, SYDNEY, 30, List.of(range("01:00", "05:00")), 30, Instant.MIN, Instant.MAX,
                List.of(sam));
        assertThat(times(SlotCalculator.compute(in)))
                .containsExactly("01:00", "01:30", "03:00", "03:30", "04:00", "04:30");
        // Brisbane has no daylight saving.
        assertThat(SlotCalculator.compute(new Input(dstStart, BRISBANE, 30, List.of(range("01:00", "05:00")), 30,
                Instant.MIN, Instant.MAX, List.of(sam)))).hasSize(8);
    }

    @Test
    void lengthsAreRealElapsedTimeWhenClocksGoBack() {
        // Sydney: 5 April 2026, 03:00 goes back to 02:00, so 01:00–04:00 local is four real hours.
        LocalDate dstEnd = LocalDate.of(2026, 4, 5);
        Candidate sam = new Candidate(SAM, List.of(range("01:00", "04:00")), List.of());
        Input in = new Input(dstEnd, SYDNEY, 60, List.of(range("01:00", "04:00")), 180, Instant.MIN, Instant.MAX,
                List.of(sam));
        assertThat(times(SlotCalculator.compute(in))).containsExactly("01:00", "02:00");
    }
}
