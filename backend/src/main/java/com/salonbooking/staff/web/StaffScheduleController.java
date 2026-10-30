package com.salonbooking.staff.web;

import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.staff.app.StaffScheduleService;
import com.salonbooking.staff.app.StaffScheduleService.Rota;
import com.salonbooking.staff.app.StaffScheduleService.Schedule;
import com.salonbooking.staff.app.StaffScheduleService.TimeOffInput;
import com.salonbooking.staff.app.StaffScheduleService.TimeOffKind;
import com.salonbooking.staff.app.StaffScheduleService.Warning;
import com.salonbooking.staff.domain.ScheduleRules.WeeklyShift;
import com.salonbooking.staff.domain.ScheduleRules.WorkInterval;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff schedules. Each change has a {@code /check} twin that returns warnings without saving, so
 * the console can list them before the person confirms (STF-7).
 */
@RestController
class StaffScheduleController {

    record ShiftRequest(@NotNull Integer weekday, @NotNull UUID locationId, @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt) {

        WeeklyShift shift() {
            return new WeeklyShift(weekday, locationId, startsAt, endsAt);
        }
    }

    record WeeklyRequest(@NotNull @Size(max = 100) List<@Valid @NotNull ShiftRequest> shifts) {

        List<WeeklyShift> pattern() {
            return shifts.stream().map(ShiftRequest::shift).toList();
        }
    }

    record IntervalRequest(@NotNull UUID locationId, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}

    /** No intervals means a day off. */
    record DayRequest(@NotNull @Size(max = 10) List<@Valid @NotNull IntervalRequest> intervals) {

        List<WorkInterval> work() {
            return intervals.stream().map(i -> new WorkInterval(i.locationId(), i.startsAt(), i.endsAt())).toList();
        }
    }

    record TimeOffRequest(@NotNull TimeOffKind kind, @NotNull LocalDate startsOn, LocalDate endsOn, LocalTime startsAt,
            LocalTime endsAt, @Size(max = 200) String reason) {

        TimeOffInput input() {
            return new TimeOffInput(kind, startsOn, endsOn, startsAt, endsAt, reason);
        }
    }

    record Warnings(List<Warning> warnings) {}

    private final StaffScheduleService schedules;

    StaffScheduleController(StaffScheduleService schedules) {
        this.schedules = schedules;
    }

    /** The week containing {@code date}, Monday to Sunday; optionally only staff of one branch. */
    @GetMapping("/api/v1/admin/schedule")
    Rota week(@AuthenticationPrincipal StaffPrincipal staff, @RequestParam LocalDate date,
            @RequestParam(required = false) UUID locationId) {
        return schedules.week(staff, date, locationId);
    }

    @GetMapping("/api/v1/admin/staff/{staffId}/schedule")
    Schedule get(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId) {
        return schedules.get(staff, staffId);
    }

    @PostMapping("/api/v1/admin/staff/{staffId}/schedule/weekly/check")
    Warnings checkWeekly(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @Valid @RequestBody WeeklyRequest request) {
        return new Warnings(schedules.checkWeekly(staff, staffId, request.pattern()));
    }

    @PutMapping("/api/v1/admin/staff/{staffId}/schedule/weekly")
    Schedule replaceWeekly(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @Valid @RequestBody WeeklyRequest request) {
        return schedules.replaceWeekly(staff, staffId, request.pattern());
    }

    @PostMapping("/api/v1/admin/staff/{staffId}/schedule/days/{date}/check")
    Warnings checkDay(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @PathVariable LocalDate date, @Valid @RequestBody DayRequest request) {
        return new Warnings(schedules.checkDay(staff, staffId, date, request.work()));
    }

    @PutMapping("/api/v1/admin/staff/{staffId}/schedule/days/{date}")
    Schedule setDay(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @PathVariable LocalDate date, @Valid @RequestBody DayRequest request) {
        return schedules.setDay(staff, staffId, date, request.work());
    }

    @DeleteMapping("/api/v1/admin/staff/{staffId}/schedule/days/{date}")
    Schedule clearDay(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @PathVariable LocalDate date) {
        return schedules.clearDay(staff, staffId, date);
    }

    @PostMapping("/api/v1/admin/staff/{staffId}/time-off/check")
    Warnings checkTimeOff(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @Valid @RequestBody TimeOffRequest request) {
        return new Warnings(schedules.checkTimeOff(staff, staffId, request.input()));
    }

    @PostMapping("/api/v1/admin/staff/{staffId}/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    Schedule addTimeOff(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @Valid @RequestBody TimeOffRequest request) {
        return schedules.addTimeOff(staff, staffId, request.input());
    }

    @DeleteMapping("/api/v1/admin/staff/{staffId}/time-off/{timeOffId}")
    Schedule removeTimeOff(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @PathVariable UUID timeOffId) {
        return schedules.removeTimeOff(staff, staffId, timeOffId);
    }
}
