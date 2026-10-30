package com.salonbooking.booking.web;

import static com.salonbooking.support.TestData.A_SYDNEY;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.OWNER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.salonbooking.support.AdminClient;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Online booking against real PostgreSQL (BKG, AVL, MGT). Glow Hair's Sydney CBD branch opens
 * Mondays 9–5; Sam and Kim both work there on Mondays and both do a one-hour cut.
 */
class BookingApiTest extends IntegrationTest {

    static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    static final String BASE = "/api/v1/public/b/glow-hair";

    @Autowired
    MockMvc mvc;

    UUID cut;
    UUID colour;
    UUID sam;
    UUID kim;
    LocalDate monday;

    @BeforeEach
    void setUp() {
        UUID category = UUID.randomUUID();
        cut = UUID.randomUUID();
        colour = UUID.randomUUID();
        sam = UUID.randomUUID();
        kim = UUID.randomUUID();
        admin.update("INSERT INTO service_category (id, business_id, name) VALUES (?, ?, 'Hair')", category, BUSINESS_A);
        admin.update("""
                INSERT INTO service (id, business_id, category_id, name, default_duration_min, buffer_after_min, default_price)
                VALUES (?, ?, ?, 'Cut', 60, 0, 8500), (?, ?, ?, 'Colour', 90, 15, 15000)""",
                cut, BUSINESS_A, category, colour, BUSINESS_A, category);
        admin.update("INSERT INTO location_service (business_id, location_id, service_id) VALUES (?, ?, ?), (?, ?, ?)",
                BUSINESS_A, A_SYDNEY, cut, BUSINESS_A, A_SYDNEY, colour);
        admin.update("INSERT INTO staff (id, business_id, display_name) VALUES (?, ?, 'Sam'), (?, ?, 'Kim')",
                sam, BUSINESS_A, kim, BUSINESS_A);
        for (UUID person : List.of(sam, kim)) {
            admin.update("INSERT INTO staff_location (business_id, staff_id, location_id) VALUES (?, ?, ?)",
                    BUSINESS_A, person, A_SYDNEY);
            admin.update("INSERT INTO staff_service (business_id, staff_id, service_id) VALUES (?, ?, ?)",
                    BUSINESS_A, person, cut);
            admin.update("""
                    INSERT INTO staff_shift (id, business_id, staff_id, location_id, weekday, starts_at, ends_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, 1, '09:00', '17:00')""", BUSINESS_A, person, A_SYDNEY);
        }
        // A Monday at least two days away, inside the 60-day horizon.
        LocalDate today = LocalDate.now(SYDNEY);
        monday = today.plusDays(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    String at(String time) {
        return ZonedDateTime.of(monday, LocalTime.parse(time), SYDNEY).toInstant().toString();
    }

    ResultActions slots(UUID staff) throws Exception {
        return mvc.perform(get(BASE + "/locations/sydney-cbd/availability")
                .param("services", cut.toString()).param("date", monday.toString())
                .param("staff", staff == null ? "" : staff.toString()));
    }

    static String booking(UUID service, UUID staff, String start, String email, String key) {
        return """
                {"locationSlug": "sydney-cbd", "serviceIds": ["%s"], "staffId": %s, "start": "%s",
                 "customer": {"fullName": "Jamie Customer", "email": "%s", "phone": "0412 345 678"},
                 "note": "First visit", "acceptsPolicy": true, "idempotencyKey": "%s"}"""
                .formatted(service, staff == null ? "null" : "\"" + staff + "\"", start, email, key);
    }

    ResultActions book(String json) throws Exception {
        return mvc.perform(post(BASE + "/bookings").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions book(UUID staff, String time, String email) throws Exception {
        return book(booking(cut, staff, at(time), email, UUID.randomUUID().toString()));
    }

    @Test
    void catalogListsWhatTheBranchOffersAndWhoDoesIt() throws Exception {
        mvc.perform(get(BASE + "/locations/sydney-cbd/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].name").value("Hair"))
                .andExpect(jsonPath("$.categories[0].services[*].name", contains("Colour", "Cut")))
                .andExpect(jsonPath("$.stylists[*].displayName", contains("Kim", "Sam")))
                .andExpect(jsonPath("$.stylists[0].serviceIds", contains(cut.toString())));
    }

    @Test
    void slotsCoverTheWorkingDayAndDaysShowWhenTheBranchIsOpen() throws Exception {
        slots(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Australia/Sydney"))
                .andExpect(jsonPath("$.durationMin").value(60))
                .andExpect(jsonPath("$.totalPriceCents").value(8500))
                .andExpect(jsonPath("$.slots[0].time").value("09:00:00"))
                .andExpect(jsonPath("$.slots[-1:].time", contains("16:00:00")))
                .andExpect(jsonPath("$.slots", hasSize(29)))
                .andExpect(jsonPath("$.slots[0].staffIds").doesNotExist());

        mvc.perform(get(BASE + "/locations/sydney-cbd/availability/days")
                        .param("services", cut.toString()).param("from", monday.toString())
                        .param("to", monday.plusDays(1).toString()))
                .andExpect(jsonPath("$.days[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.days[1].status").value("CLOSED"));
    }

    @Test
    void servicesNobodyDoesAreReportedClearly() throws Exception {
        mvc.perform(get(BASE + "/locations/sydney-cbd/availability")
                        .param("services", cut + "," + colour).param("date", monday.toString()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("NO_QUALIFIED_STYLIST"));
    }

    @Test
    void bookingSnapshotsServicesAndTheTimeIsTakenForThatStylist() throws Exception {
        String body = book(sam, "10:00", "jamie@example.test")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.reference").value(org.hamcrest.Matchers.matchesPattern("[A-Z0-9]{8}")))
                .andExpect(jsonPath("$.booking.staffName").value("Sam"))
                .andExpect(jsonPath("$.booking.services[0].name").value("Cut"))
                .andExpect(jsonPath("$.booking.totalPriceCents").value(8500))
                .andExpect(jsonPath("$.booking.email").value("j***@example.test"))
                .andExpect(jsonPath("$.booking.phone").value("*** *** 678"))
                .andExpect(jsonPath("$.manageToken").isString())
                .andReturn().getResponse().getContentAsString();

        slots(sam).andExpect(jsonPath("$.slots[*].time", not(org.hamcrest.Matchers.hasItem("10:00:00"))))
                // Back-to-back is fine; 09:30 would overlap.
                .andExpect(jsonPath("$.slots[*].time", org.hamcrest.Matchers.hasItem("11:00:00")))
                .andExpect(jsonPath("$.slots[*].time", not(org.hamcrest.Matchers.hasItem("09:30:00"))));
        slots(null).andExpect(jsonPath("$.slots[*].time", org.hamcrest.Matchers.hasItem("10:00:00")));

        // Editing the service later does not change the booking (BKG-12).
        admin.update("UPDATE service SET name = 'Cut & finish', default_price = 9900 WHERE id = ?", cut);
        String token = JsonPath.read(body, "$.manageToken");
        mvc.perform(get("/api/v1/public/manage/{token}", token))
                .andExpect(jsonPath("$.services[0].name").value("Cut"))
                .andExpect(jsonPath("$.services[0].priceCents").value(8500));
    }

    @Test
    void aStylistCannotBeBookedTwiceForTheSameTime() throws Exception {
        book(sam, "10:00", "a@example.test").andExpect(status().isCreated());
        book(sam, "10:30", "b@example.test")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
    }

    @Test
    void anyStylistFillsUpOnePersonAtATime() throws Exception {
        book(null, "10:00", "a@example.test").andExpect(status().isCreated());
        book(null, "10:00", "b@example.test").andExpect(status().isCreated());
        book(null, "10:00", "c@example.test").andExpect(status().isConflict());
        assertThat(admin.queryForList("SELECT staff_id FROM appointment", UUID.class)).containsExactlyInAnyOrder(sam, kim);
    }

    @Test
    void twentyParallelRequestsForOneStylistAndTimeBookItOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String json = booking(cut, sam, at("11:00"), "p" + i + "@example.test", UUID.randomUUID().toString());
            attempts.add(() -> book(json).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(attempts)) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != 201).allMatch(s -> s == 409);
        assertThat(admin.queryForObject("SELECT count(*) FROM appointment", Integer.class)).isEqualTo(1);
    }

    @Test
    void retryingWithTheSameKeyReturnsTheSameBooking() throws Exception {
        String json = booking(cut, null, at("12:00"), "retry@example.test", UUID.randomUUID().toString());
        String first = JsonPath.read(book(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.booking.reference");
        String second = JsonPath.read(book(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.booking.reference");
        assertThat(second).isEqualTo(first);
        assertThat(admin.queryForObject("SELECT count(*) FROM appointment", Integer.class)).isEqualTo(1);
        assertThat(admin.queryForObject("SELECT count(*) FROM booking_token", Integer.class)).isEqualTo(2);
    }

    @Test
    void oneContactCanHoldOnlyALimitedNumberOfFutureBookings() throws Exception {
        admin.update("UPDATE business SET max_future_bookings_per_contact = 1 WHERE id = ?", BUSINESS_A);
        book(sam, "09:00", "same@example.test").andExpect(status().isCreated());
        // Same mobile, different email: still the same person.
        book(kim, "13:00", "other@example.test")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_LIMIT_REACHED"));
        assertThat(admin.queryForObject("SELECT count(*) FROM customer", Integer.class)).isEqualTo(1);
    }

    @Test
    void customersCancelThroughTheirLinkAndTheTimeFreesUp() throws Exception {
        String token = JsonPath.read(book(sam, "14:00", "cancel@example.test").andReturn().getResponse()
                .getContentAsString(), "$.manageToken");

        mvc.perform(get("/api/v1/public/manage/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.branch.name").value("Sydney CBD"));
        mvc.perform(post("/api/v1/public/manage/{token}/cancel", token))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.canCancel").value(false));
        mvc.perform(post("/api/v1/public/manage/{token}/cancel", token))
                .andExpect(status().isConflict());
        book(sam, "14:00", "next@example.test").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/public/manage/{token}", "not-a-real-token")).andExpect(status().isNotFound());
    }

    @Test
    void cancellingAfterTheCutOffIsRefused() throws Exception {
        String token = JsonPath.read(book(sam, "15:00", "late@example.test").andReturn().getResponse()
                .getContentAsString(), "$.manageToken");
        admin.update("UPDATE business SET cancel_cutoff_min = 60 * 24 * 30 WHERE id = ?", BUSINESS_A);

        mvc.perform(get("/api/v1/public/manage/{token}", token)).andExpect(jsonPath("$.canCancel").value(false));
        mvc.perform(post("/api/v1/public/manage/{token}/cancel", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("POLICY_CUTOFF_PASSED"));
    }

    @Test
    void bookingsRequireValidDetailsAndPolicyAgreement() throws Exception {
        String valid = booking(cut, sam, at("10:00"), "v@example.test", UUID.randomUUID().toString());
        book(valid.replace("\"acceptsPolicy\": true", "\"acceptsPolicy\": false")).andExpect(status().isBadRequest());
        book(valid.replace("0412 345 678", "call me")).andExpect(status().isBadRequest());
        book(valid.replace(at("10:00"), at("10:07"))).andExpect(status().isConflict());
        assertThat(admin.queryForObject("SELECT count(*) FROM appointment", Integer.class)).isZero();
    }

    @Test
    void staffSeeBookingsAndScheduleChangesWarnAboutThem() throws Exception {
        book(sam, "10:00", "agenda@example.test").andExpect(status().isCreated());
        AdminClient owner = new AdminClient(mvc, new StaffClient(mvc).androidToken(OWNER_A));

        owner.get("/api/v1/staff/agenda?date={date}", monday)
                .andExpect(jsonPath("$[0].staffName").value("Sam"))
                .andExpect(jsonPath("$[0].customerName").value("Jamie Customer"))
                .andExpect(jsonPath("$[0].customerPhone").value("+61412345678"))
                .andExpect(jsonPath("$[0].services").value("Cut"))
                .andExpect(jsonPath("$[0].customerNote").value("First visit"));

        owner.post("/api/v1/admin/staff/{id}/schedule/days/{date}/check", "{\"intervals\": []}", sam, monday)
                .andExpect(jsonPath("$.warnings[0].code").value("BOOKED_APPOINTMENT"))
                .andExpect(jsonPath("$.warnings[0].message", containsString("Cut")));
        owner.post("/api/v1/admin/staff/{id}/schedule/weekly/check", """
                        {"shifts": [{"weekday": 1, "locationId": "%s", "startsAt": "12:00", "endsAt": "17:00"}]}"""
                        .formatted(A_SYDNEY), sam)
                .andExpect(jsonPath("$.warnings[*].code", contains("BOOKED_APPOINTMENT")));
        owner.post("/api/v1/admin/staff/{id}/time-off/check", """
                        {"kind": "BREAK", "startsOn": "%s", "startsAt": "10:30", "endsAt": "11:00"}""".formatted(monday), sam)
                .andExpect(jsonPath("$.warnings[0].message", containsString("during this break")));
        owner.post("/api/v1/admin/staff/{id}/time-off/check", """
                        {"kind": "BREAK", "startsOn": "%s", "startsAt": "11:00", "endsAt": "11:30"}""".formatted(monday), sam)
                .andExpect(jsonPath("$.warnings", empty()));
        owner.post("/api/v1/admin/staff/{id}/schedule/days/{date}/check", "{\"intervals\": []}", kim, monday)
                .andExpect(jsonPath("$.warnings", empty()));
    }

    @Test
    void leaveAndBreaksRemoveTimesFromSale() throws Exception {
        admin.update("""
                INSERT INTO staff_time_off (id, business_id, staff_id, kind, starts_on, ends_on)
                VALUES (gen_random_uuid(), ?, ?, 'leave', ?, ?)""", BUSINESS_A, sam, monday, monday);
        admin.update("""
                INSERT INTO staff_time_off (id, business_id, staff_id, kind, starts_on, ends_on, starts_at, ends_at)
                VALUES (gen_random_uuid(), ?, ?, 'break', ?, ?, '12:00', '13:00')""", BUSINESS_A, kim, monday, monday);

        slots(sam).andExpect(jsonPath("$.slots", empty()));
        slots(kim).andExpect(jsonPath("$.slots[*].time", not(org.hamcrest.Matchers.hasItem("11:30:00"))))
                .andExpect(jsonPath("$.slots[*].time", org.hamcrest.Matchers.hasItem("11:00:00")))
                .andExpect(jsonPath("$.slots[*].time", org.hamcrest.Matchers.hasItem("13:00:00")));
        mvc.perform(get(BASE + "/locations/sydney-cbd/availability/days")
                        .param("services", cut.toString()).param("staff", sam.toString())
                        .param("from", monday.toString()).param("to", monday.toString()))
                .andExpect(jsonPath("$.days[0].status").value("FULL"))
                .andExpect(jsonPath("$.lastBookableDate", not(nullValue())));
    }

    // ---- Customer reschedule (MGT-4) ----------------------------------------------------------

    String tokenFor(UUID staff, String time, String email) throws Exception {
        return JsonPath.read(book(staff, time, email).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.manageToken");
    }

    ResultActions moveTo(String token, String time, UUID staff) throws Exception {
        return mvc.perform(post("/api/v1/public/manage/{token}/reschedule", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"start\": \"%s\", \"staffId\": %s}".formatted(at(time), staff == null ? "null" : "\"" + staff + "\"")));
    }

    @Test
    void customersMoveTheirBookingAndKeepTheirStylistWhenFree() throws Exception {
        String token = tokenFor(sam, "10:00", "move@example.test");

        mvc.perform(get("/api/v1/public/manage/{token}/options", token))
                .andExpect(jsonPath("$.currentStaffId").value(sam.toString()))
                .andExpect(jsonPath("$.stylists[*].displayName", contains("Kim", "Sam")));
        // Its own time does not block it.
        mvc.perform(get("/api/v1/public/manage/{token}/availability", token).param("date", monday.toString())
                        .param("staff", sam.toString()))
                .andExpect(jsonPath("$.slots[*].time", org.hamcrest.Matchers.hasItem("10:30:00")));

        moveTo(token, "13:00", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffName").value("Sam"))
                .andExpect(jsonPath("$.startAt").value(org.hamcrest.Matchers.startsWith(at("13:00").substring(0, 16))));
        book(sam, "10:00", "next@example.test").andExpect(status().isCreated());
        assertThat(admin.queryForList("SELECT type FROM notification ORDER BY created_at", String.class))
                .contains("booking_rescheduled");
    }

    @Test
    void aTakenTimeLeavesTheBookingWhereItWas() throws Exception {
        String token = tokenFor(sam, "10:00", "stay@example.test");
        book(sam, "14:00", "other@example.test").andExpect(status().isCreated());

        moveTo(token, "14:00", sam)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
        // "Anyone" takes Kim, who is free then.
        moveTo(token, "14:00", null).andExpect(jsonPath("$.staffName").value("Kim"));
        mvc.perform(get("/api/v1/public/manage/{token}", token)).andExpect(jsonPath("$.staffName").value("Kim"));
    }

    @Test
    void movingIsRefusedAfterTheCutOffOrOnceCancelled() throws Exception {
        String late = tokenFor(sam, "10:00", "late@example.test");
        String cancelled = tokenFor(sam, "12:00", "gone@example.test");
        mvc.perform(post("/api/v1/public/manage/{token}/cancel", cancelled)).andExpect(status().isOk());
        admin.update("UPDATE business SET cancel_cutoff_min = 60 * 24 * 30 WHERE id = ?", BUSINESS_A);

        moveTo(late, "15:00", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("POLICY_CUTOFF_PASSED"));
        moveTo(cancelled, "15:00", null).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/public/manage/{token}", late))
                .andExpect(jsonPath("$.startAt").value(org.hamcrest.Matchers.startsWith(at("10:00").substring(0, 16))));
    }

    // ---- Booking rules (DSH-17) -----------------------------------------------------------------

    @Test
    void ownersSetTheBookingRulesAndBranchesCanOverrideThem() throws Exception {
        com.salonbooking.support.AdminClient owner = new com.salonbooking.support.AdminClient(mvc,
                new com.salonbooking.support.StaffClient(mvc).androidToken(OWNER_A));
        owner.get("/api/v1/admin/business").andExpect(jsonPath("$.policy.slotIntervalMin").value(15))
                .andExpect(jsonPath("$.policy.cancelCutoffMin").value(1440));

        // A branch with half-hourly start times offers half as many.
        owner.put("/api/v1/admin/locations/{id}/policy", "{\"slotIntervalMin\": 30}", A_SYDNEY)
                .andExpect(jsonPath("$.policy.slotIntervalMin").value(30))
                .andExpect(jsonPath("$.policy.minNoticeMin").value(nullValue()));
        slots(null).andExpect(jsonPath("$.slots", hasSize(15)));
        owner.put("/api/v1/admin/locations/{id}/policy", "{}", A_SYDNEY).andExpect(jsonPath("$.policy.slotIntervalMin").value(nullValue()));
        slots(null).andExpect(jsonPath("$.slots", hasSize(29)));

        // Booking only a day ahead puts Monday out of reach; the cut-off shows on the public page.
        owner.put("/api/v1/admin/business/policy", """
                        {"slotIntervalMin": 15, "minNoticeMin": 60, "horizonDays": 1, "cancelCutoffMin": 120,
                         "maxFutureBookingsPerContact": 2}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.horizonDays").value(1));
        slots(null).andExpect(jsonPath("$.slots", empty()));
        mvc.perform(get(BASE)).andExpect(jsonPath("$.cancelCutoffMin").value(120));

        owner.put("/api/v1/admin/business/policy", """
                        {"slotIntervalMin": 7, "minNoticeMin": 60, "horizonDays": 30, "cancelCutoffMin": 120,
                         "maxFutureBookingsPerContact": 2}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message", containsString("every 5, 10, 15")));
        owner.put("/api/v1/admin/locations/{id}/policy", "{\"horizonDays\": 0}", A_SYDNEY).andExpect(status().isBadRequest());
    }

    @Test
    void onlyOwnersChangeBookingRules() throws Exception {
        String token = new com.salonbooking.support.StaffClient(mvc).androidToken(com.salonbooking.support.TestData.MULTI);
        com.salonbooking.support.AdminClient manager = new com.salonbooking.support.AdminClient(mvc, token);
        manager.post("/api/v1/auth/business", "{\"businessId\": \"%s\"}".formatted(com.salonbooking.support.TestData.BUSINESS_B));
        manager.put("/api/v1/admin/business/policy", """
                        {"slotIntervalMin": 15, "minNoticeMin": 60, "horizonDays": 30, "cancelCutoffMin": 120,
                         "maxFutureBookingsPerContact": 2}""").andExpect(status().isForbidden());
        manager.put("/api/v1/admin/locations/{id}/policy", "{\"slotIntervalMin\": 30}", com.salonbooking.support.TestData.B_BRISBANE)
                .andExpect(status().isForbidden());
    }
}
