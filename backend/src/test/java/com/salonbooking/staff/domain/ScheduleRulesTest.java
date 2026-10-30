package com.salonbooking.staff.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.salonbooking.staff.domain.ScheduleRules.WeeklyShift;
import com.salonbooking.staff.domain.ScheduleRules.WorkInterval;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleRulesTest {

    static final UUID CITY = UUID.randomUUID();
    static final UUID SUBURB = UUID.randomUUID();
    static final LocalDate MONDAY = LocalDate.of(2030, 6, 3);

    static WorkInterval at(UUID location, String from, String to) {
        return new WorkInterval(location, LocalTime.parse(from), LocalTime.parse(to));
    }

    static final List<WeeklyShift> PATTERN = List.of(
            new WeeklyShift(1, CITY, LocalTime.parse("13:30"), LocalTime.parse("17:00")),
            new WeeklyShift(1, CITY, LocalTime.parse("09:00"), LocalTime.parse("12:30")),
            new WeeklyShift(2, SUBURB, LocalTime.parse("10:00"), LocalTime.parse("16:00")));

    @Test
    void weeklyPatternAppliesOnItsWeekdayInTimeOrder() {
        assertThat(ScheduleRules.workingOn(MONDAY, PATTERN, Optional.empty()))
                .containsExactly(at(CITY, "09:00", "12:30"), at(CITY, "13:30", "17:00"));
        assertThat(ScheduleRules.workingOn(MONDAY.plusDays(1), PATTERN, Optional.empty()))
                .containsExactly(at(SUBURB, "10:00", "16:00"));
        assertThat(ScheduleRules.workingOn(MONDAY.plusDays(2), PATTERN, Optional.empty())).isEmpty();
    }

    @Test
    void anOverrideReplacesTheWholeDay() {
        assertThat(ScheduleRules.workingOn(MONDAY, PATTERN, Optional.of(List.of()))).isEmpty();
        assertThat(ScheduleRules.workingOn(MONDAY, PATTERN, Optional.of(List.of(at(SUBURB, "12:00", "18:00")))))
                .containsExactly(at(SUBURB, "12:00", "18:00"));
    }

    @Test
    void overlapsAreRejectedButBackToBackShiftsAreFine() {
        assertThat(ScheduleRules.problemWith(List.of(at(CITY, "09:00", "12:30"), at(SUBURB, "12:30", "17:00")))).isEmpty();
        assertThat(ScheduleRules.problemWith(List.of(at(CITY, "09:00", "13:00"), at(SUBURB, "12:00", "17:00"))))
                .hasValueSatisfying(p -> assertThat(p).contains("two branches"));
        assertThat(ScheduleRules.problemWith(List.of(at(CITY, "09:00", "13:00"), at(CITY, "12:00", "17:00"))))
                .hasValueSatisfying(p -> assertThat(p).contains("overlap"));
        assertThat(ScheduleRules.problemWith(List.of(at(CITY, "17:00", "09:00")))).isPresent();
    }

    @Test
    void withinHoursNeedsOneOpeningRangeToCoverTheWholeShift() {
        List<WorkInterval> open = List.of(at(CITY, "09:00", "12:30"), at(CITY, "13:30", "17:00"));
        assertThat(ScheduleRules.withinHours(at(CITY, "09:00", "12:30"), open)).isTrue();
        assertThat(ScheduleRules.withinHours(at(CITY, "08:30", "12:00"), open)).isFalse();
        assertThat(ScheduleRules.withinHours(at(CITY, "12:00", "14:00"), open)).isFalse();
    }
}
