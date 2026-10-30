package com.salonbooking.staff.web;

import static com.salonbooking.support.TestData.A_PARRAMATTA;
import static com.salonbooking.support.TestData.A_SYDNEY;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.BUSINESS_B;
import static com.salonbooking.support.TestData.B_BRISBANE;
import static com.salonbooking.support.TestData.B_NORTH_ONLY;
import static com.salonbooking.support.TestData.MULTI;
import static com.salonbooking.support.TestData.OWNER_A;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.salonbooking.support.AdminClient;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Weekly shifts, date overrides, leave and breaks (STF-4..7). Glow Hair's Sydney branch opens
 * Mondays 9–5 and Saturdays 9–1; Parramatta has no hours. 3 June 2030 is a Monday.
 */
class StaffScheduleApiTest extends IntegrationTest {

    static final String MONDAY = "2030-06-03";

    @Autowired
    MockMvc mvc;

    AdminClient owner;
    String sam;

    @BeforeEach
    void setUp() throws Exception {
        owner = new AdminClient(mvc, new StaffClient(mvc).androidToken(OWNER_A));
        sam = JsonPath.read(owner.post("/api/v1/admin/staff", """
                        {"displayName": "Sam", "locationIds": ["%s", "%s"]}""".formatted(A_SYDNEY, A_PARRAMATTA))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    static String shift(int weekday, UUID location, String from, String to) {
        return """
                {"weekday": %d, "locationId": "%s", "startsAt": "%s", "endsAt": "%s"}""".formatted(weekday, location, from, to);
    }

    static String weekly(String... shifts) {
        return "{\"shifts\": [" + String.join(",", shifts) + "]}";
    }

    static String day(String... intervals) {
        return "{\"intervals\": [" + String.join(",", intervals) + "]}";
    }

    static String work(UUID location, String from, String to) {
        return """
                {"locationId": "%s", "startsAt": "%s", "endsAt": "%s"}""".formatted(location, from, to);
    }

    @Test
    void weeklyPatternCanSpanBranchesButNotOverlap() throws Exception {
        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(
                        shift(1, A_SYDNEY, "09:00", "12:30"), shift(1, A_SYDNEY, "13:30", "17:00"),
                        shift(2, A_PARRAMATTA, "10:00", "16:00")), sam)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly", hasSize(3)))
                .andExpect(jsonPath("$.weekly[0].startsAt").value("09:00:00"));

        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(
                        shift(1, A_SYDNEY, "09:00", "13:00"), shift(1, A_PARRAMATTA, "12:00", "17:00")), sam)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("two branches")));
        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(shift(3, A_SYDNEY, "17:00", "09:00")), sam)
                .andExpect(status().isBadRequest());
        owner.get("/api/v1/admin/staff/{id}/schedule", sam).andExpect(jsonPath("$.weekly", hasSize(3)));
    }

    @Test
    void shiftsNeedTheBranchOnTheProfile() throws Exception {
        owner.put("/api/v1/admin/staff/{id}", """
                {"displayName": "Sam", "locationIds": ["%s"]}""".formatted(A_SYDNEY), sam);

        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(shift(2, A_PARRAMATTA, "10:00", "16:00")), sam)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("doesn't work at that branch")));
    }

    @Test
    void removingABranchFromTheProfileRemovesTheShiftsThereOnly() throws Exception {
        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(
                shift(1, A_SYDNEY, "09:00", "17:00"), shift(2, A_PARRAMATTA, "10:00", "16:00")), sam);

        owner.put("/api/v1/admin/staff/{id}", """
                {"displayName": "Sam", "locationIds": ["%s"]}""".formatted(A_SYDNEY), sam).andExpect(status().isOk());

        owner.get("/api/v1/admin/staff/{id}/schedule", sam)
                .andExpect(jsonPath("$.weekly[*].locationId", contains(A_SYDNEY.toString())));
    }

    @Test
    void checkListsWarningsWithoutSaving() throws Exception {
        owner.post("/api/v1/admin/staff/{id}/schedule/weekly/check", weekly(
                        shift(1, A_SYDNEY, "08:00", "17:00"), shift(1, A_SYDNEY, "17:00", "17:30"),
                        shift(2, A_PARRAMATTA, "10:00", "16:00"), shift(6, A_SYDNEY, "09:00", "13:00")), sam)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[*].code", contains("OUTSIDE_HOURS", "OUTSIDE_HOURS", "BRANCH_CLOSED")))
                .andExpect(jsonPath("$.warnings[0].message", containsString("Monday 8:00")))
                .andExpect(jsonPath("$.warnings[2].message", containsString("Parramatta is closed on Tuesdays")));
        owner.get("/api/v1/admin/staff/{id}/schedule", sam).andExpect(jsonPath("$.weekly", empty()));

        // Impossible schedules are errors, not warnings.
        owner.post("/api/v1/admin/staff/{id}/schedule/weekly/check", weekly(
                        shift(1, A_SYDNEY, "09:00", "13:00"), shift(1, A_PARRAMATTA, "12:00", "17:00")), sam)
                .andExpect(status().isBadRequest());
    }

    @Test
    void dayOverridesReplaceThePatternAndShowInTheRota() throws Exception {
        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(
                shift(1, A_SYDNEY, "09:00", "17:00"), shift(2, A_PARRAMATTA, "10:00", "16:00")), sam);

        owner.put("/api/v1/admin/staff/{id}/schedule/days/{date}", day(), sam, MONDAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides[0].date").value(MONDAY))
                .andExpect(jsonPath("$.overrides[0].intervals", empty()));
        owner.put("/api/v1/admin/staff/{id}/schedule/days/{date}", day(work(A_SYDNEY, "12:00", "17:00")), sam,
                "2030-06-04").andExpect(status().isOk());

        owner.get("/api/v1/admin/schedule?date=2030-06-05")
                .andExpect(jsonPath("$.weekStart").value(MONDAY))
                .andExpect(jsonPath("$.staff[0].displayName").value("Sam"))
                .andExpect(jsonPath("$.staff[0].days", hasSize(7)))
                .andExpect(jsonPath("$.staff[0].days[0].override").value(true))
                .andExpect(jsonPath("$.staff[0].days[0].intervals", empty()))
                .andExpect(jsonPath("$.staff[0].days[1].intervals[0].locationId").value(A_SYDNEY.toString()))
                .andExpect(jsonPath("$.staff[0].days[1].intervals[0].startsAt").value("12:00:00"));

        owner.delete("/api/v1/admin/staff/{id}/schedule/days/{date}", sam, MONDAY)
                .andExpect(jsonPath("$.overrides", hasSize(1)));
        owner.get("/api/v1/admin/schedule?date=2030-06-03&locationId={id}", A_SYDNEY)
                .andExpect(jsonPath("$.staff[0].days[0].override").value(false))
                .andExpect(jsonPath("$.staff[0].days[0].intervals[0].startsAt").value("09:00:00"));
    }

    @Test
    void leaveBreaksAndClosuresAreWarnedAboutOnADay() throws Exception {
        owner.post("/api/v1/admin/staff/{id}/time-off", """
                        {"kind": "LEAVE", "startsOn": "2030-06-04", "endsOn": "2030-06-05", "reason": "Holiday"}""", sam)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeOff[0].kind").value("LEAVE"));
        owner.post("/api/v1/admin/staff/{id}/time-off", """
                        {"kind": "BREAK", "startsOn": "%s", "startsAt": "15:00", "endsAt": "16:00", "reason": "Dentist"}"""
                        .formatted(MONDAY), sam)
                .andExpect(jsonPath("$.timeOff[0].kind").value("BREAK"))
                .andExpect(jsonPath("$.timeOff[0].endsOn").value(MONDAY));
        owner.post("/api/v1/admin/locations/{id}/closures", """
                {"startsOn": "2030-06-10", "endsOn": "2030-06-10", "reason": "Public holiday"}""", A_SYDNEY);

        owner.post("/api/v1/admin/staff/{id}/schedule/days/{date}/check", day(work(A_SYDNEY, "09:00", "17:00")), sam,
                        "2030-06-04")
                .andExpect(jsonPath("$.warnings[*].code", contains("BRANCH_CLOSED", "ON_LEAVE")));
        owner.post("/api/v1/admin/staff/{id}/schedule/days/{date}/check", day(work(A_SYDNEY, "09:00", "17:00")), sam,
                        "2030-06-10")
                .andExpect(jsonPath("$.warnings[0].message", containsString("Public holiday")));
        owner.post("/api/v1/admin/staff/{id}/schedule/days/{date}/check", day(work(A_SYDNEY, "09:00", "17:00")), sam,
                        MONDAY)
                .andExpect(jsonPath("$.warnings", empty()));

        owner.get("/api/v1/admin/schedule?date=2030-06-03")
                .andExpect(jsonPath("$.staff[0].days[0].timeOff[0].reason").value("Dentist"))
                .andExpect(jsonPath("$.staff[0].days[1].timeOff[0].kind").value("LEAVE"))
                .andExpect(jsonPath("$.staff[0].days[2].timeOff[0].kind").value("LEAVE"))
                .andExpect(jsonPath("$.staff[0].days[3].timeOff", empty()))
                .andExpect(jsonPath("$.closures", hasSize(0)));
        owner.get("/api/v1/admin/schedule?date=2030-06-10").andExpect(jsonPath("$.closures[0].reason").value("Public holiday"));

        String leaveId = JsonPath.read(owner.get("/api/v1/admin/staff/{id}/schedule", sam)
                .andReturn().getResponse().getContentAsString(), "$.timeOff[1].id");
        owner.delete("/api/v1/admin/staff/{id}/time-off/{timeOffId}", sam, leaveId)
                .andExpect(jsonPath("$.timeOff", hasSize(1)));
    }

    @Test
    void timeOffIsValidated() throws Exception {
        owner.post("/api/v1/admin/staff/{id}/time-off", """
                {"kind": "LEAVE", "startsOn": "2030-06-05", "endsOn": "2030-06-04"}""", sam).andExpect(status().isBadRequest());
        owner.post("/api/v1/admin/staff/{id}/time-off", """
                {"kind": "BREAK", "startsOn": "2030-06-05", "startsAt": "16:00", "endsAt": "15:00"}""", sam)
                .andExpect(status().isBadRequest());
        owner.post("/api/v1/admin/staff/{id}/time-off", """
                {"kind": "BREAK", "startsOn": "2030-06-05"}""", sam).andExpect(status().isBadRequest());
    }

    @Test
    void managersChangeOnlyWorkAtTheirBranches() throws Exception {
        // North Cuts: Alex manages Brisbane only. Kim works at both branches; Lee only at North Only.
        UUID kim = UUID.randomUUID();
        UUID lee = UUID.randomUUID();
        admin.update("INSERT INTO staff (id, business_id, display_name) VALUES (?, ?, 'Kim'), (?, ?, 'Lee')",
                kim, BUSINESS_B, lee, BUSINESS_B);
        admin.update("INSERT INTO staff_location (business_id, staff_id, location_id) VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?)",
                BUSINESS_B, kim, B_BRISBANE, BUSINESS_B, kim, B_NORTH_ONLY, BUSINESS_B, lee, B_NORTH_ONLY);
        admin.update("""
                INSERT INTO staff_shift (id, business_id, staff_id, location_id, weekday, starts_at, ends_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 3, '09:00', '17:00')""", BUSINESS_B, kim, B_NORTH_ONLY);

        AdminClient manager = new AdminClient(mvc, new StaffClient(mvc).androidToken(MULTI));
        manager.post("/api/v1/auth/business", "{\"businessId\": \"%s\"}".formatted(BUSINESS_B)).andExpect(status().isOk());

        // The Wednesday at North Only is kept when the manager saves Brisbane shifts.
        manager.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(shift(1, B_BRISBANE, "08:00", "18:00")), kim)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekly", hasSize(2)));
        manager.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(shift(3, B_BRISBANE, "12:00", "18:00")), kim)
                .andExpect(status().isBadRequest());
        manager.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(shift(2, B_NORTH_ONLY, "08:00", "18:00")), kim)
                .andExpect(status().isForbidden());
        // 5 June 2030 is a Wednesday, when Kim works at North Only.
        manager.put("/api/v1/admin/staff/{id}/schedule/days/{date}", day(), kim, "2030-06-05")
                .andExpect(status().isForbidden());
        manager.put("/api/v1/admin/staff/{id}/schedule/days/{date}", day(), kim, MONDAY).andExpect(status().isOk());
        manager.post("/api/v1/admin/staff/{id}/time-off", """
                {"kind": "LEAVE", "startsOn": "2030-07-01", "endsOn": "2030-07-05"}""", kim).andExpect(status().isCreated());

        manager.get("/api/v1/admin/staff/{id}/schedule", lee).andExpect(status().isNotFound());
        manager.get("/api/v1/admin/schedule?date=2030-06-03")
                .andExpect(jsonPath("$.staff[*].displayName", contains("Kim")));
        manager.get("/api/v1/admin/schedule?date=2030-06-03&locationId={id}", B_NORTH_ONLY)
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherBusinessesStaffCannotBeReached() throws Exception {
        UUID other = UUID.randomUUID();
        admin.update("INSERT INTO staff (id, business_id, display_name) VALUES (?, ?, 'Other')", other, BUSINESS_B);

        owner.get("/api/v1/admin/staff/{id}/schedule", other).andExpect(status().isNotFound());
        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", weekly(), other).andExpect(status().isNotFound());
        owner.post("/api/v1/admin/staff/{id}/time-off", """
                {"kind": "LEAVE", "startsOn": "2030-07-01"}""", other).andExpect(status().isNotFound());
        owner.get("/api/v1/admin/schedule?date=2030-06-03")
                .andExpect(jsonPath("$.staff[*].displayName", contains("Sam")));
        owner.get("/api/v1/admin/schedule?date=2030-06-03&locationId={id}", B_BRISBANE).andExpect(status().isNotFound());
    }
}
