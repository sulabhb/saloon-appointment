package com.salonbooking.booking.web;

import static com.salonbooking.support.TestData.A_PARRAMATTA;
import static com.salonbooking.support.TestData.A_SYDNEY;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.MULTI;
import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.RECEPTION_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Staff-side booking (DSH, STS). Sydney CBD opens Mondays 9–5; Sam and Kim work there on Mondays
 * and both do a one-hour cut. Riley is reception at Sydney; Olivia is the owner.
 */
class StaffBookingApiTest extends IntegrationTest {

    static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Autowired
    MockMvc mvc;

    AdminClient owner;
    AdminClient reception;
    UUID cut;
    UUID sam;
    UUID kim;
    LocalDate monday;

    @BeforeEach
    void setUp() throws Exception {
        UUID category = UUID.randomUUID();
        cut = UUID.randomUUID();
        sam = UUID.randomUUID();
        kim = UUID.randomUUID();
        admin.update("INSERT INTO service_category (id, business_id, name) VALUES (?, ?, 'Hair')", category, BUSINESS_A);
        admin.update("""
                INSERT INTO service (id, business_id, category_id, name, default_duration_min, buffer_after_min, default_price)
                VALUES (?, ?, ?, 'Cut', 60, 0, 8500)""", cut, BUSINESS_A, category);
        admin.update("INSERT INTO location_service (business_id, location_id, service_id) VALUES (?, ?, ?), (?, ?, ?)",
                BUSINESS_A, A_SYDNEY, cut, BUSINESS_A, A_PARRAMATTA, cut);
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
        monday = LocalDate.now(SYDNEY).plusDays(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        owner = new AdminClient(mvc, new StaffClient(mvc).androidToken(OWNER_A));
        reception = new AdminClient(mvc, new StaffClient(mvc).androidToken(RECEPTION_A));
    }

    String at(String time) {
        return ZonedDateTime.of(monday, LocalTime.parse(time), SYDNEY).toInstant().toString();
    }

    static String booking(UUID location, UUID staff, String start, String customer, boolean walkIn) {
        return """
                {"locationId": "%s", "serviceIds": ["%s"], "staffId": %s, "start": "%s", %s,
                 "internalNote": "Prefers a quiet chair", "walkIn": %s, "idempotencyKey": "%s"}"""
                .formatted(location, "%CUT%", staff == null ? "null" : "\"" + staff + "\"", start, customer, walkIn,
                        UUID.randomUUID());
    }

    ResultActions book(AdminClient as, UUID staff, String time, String customer, boolean walkIn) throws Exception {
        return as.post("/api/v1/staff/appointments",
                booking(A_SYDNEY, staff, at(time), customer, walkIn).replace("%CUT%", cut.toString()));
    }

    static final String NEW_CUSTOMER = "\"customer\": {\"fullName\": \"Pat Phone\", \"phone\": \"0400 111 222\"}";

    String bookId(AdminClient as, UUID staff, String time) throws Exception {
        return JsonPath.read(book(as, staff, time, NEW_CUSTOMER, false).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void receptionBooksAPhoneCustomerAndCannotDoubleBook() throws Exception {
        book(reception, sam, "10:00", NEW_CUSTOMER, false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.source").value("staff"))
                .andExpect(jsonPath("$.customer.phone").value("+61400111222"))
                .andExpect(jsonPath("$.internalNote").value("Prefers a quiet chair"))
                .andExpect(jsonPath("$.createdByName").value("Riley Reception"))
                .andExpect(jsonPath("$.allowedTransitions", contains("CHECKED_IN", "CANCELLED")));

        book(reception, sam, "10:30", NEW_CUSTOMER, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
        // Contact details are needed unless it is a walk-in (D-4).
        book(reception, kim, "10:00", "\"customer\": {\"fullName\": \"No Contact\"}", false).andExpect(status().isBadRequest());
    }

    @Test
    void walkInsStartCheckedInAndNeedOnlyAName() throws Exception {
        book(reception, null, "09:00", "\"customer\": {\"fullName\": \"Drop In\"}", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.source").value("walk_in"))
                .andExpect(jsonPath("$.customer.email", nullValue()))
                .andExpect(jsonPath("$.allowedTransitions", contains("COMPLETED")));
    }

    @Test
    void staffCanBookStylistsWhoAreNotBookableOnline() throws Exception {
        admin.update("UPDATE staff SET bookable_online = false WHERE id = ?", sam);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/public/b/glow-hair/locations/sydney-cbd/catalog"))
                .andExpect(jsonPath("$.stylists[*].displayName", contains("Kim")));
        reception.get("/api/v1/staff/booking-options?locationId={id}", A_SYDNEY)
                .andExpect(jsonPath("$.stylists[*].displayName", contains("Kim", "Sam")));
        reception.get("/api/v1/staff/availability?locationId={l}&services={s}&staffId={st}&date={d}", A_SYDNEY, cut, sam, monday)
                .andExpect(jsonPath("$.slots[0].time").value("09:00:00"))
                .andExpect(jsonPath("$.slots[0].staffIds", contains(sam.toString())));
        book(reception, sam, "09:00", NEW_CUSTOMER, false).andExpect(status().isCreated());
    }

    @Test
    void customersAreFoundByNamePhoneOrReference() throws Exception {
        String body = book(reception, sam, "10:00", "\"customer\": {\"fullName\": \"Morgan Lee\", \"email\": \"morgan@example.test\", \"phone\": \"0400 555 666\"}", false)
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(body, "$.reference");
        String customerId = JsonPath.read(body, "$.customer.id");

        reception.get("/api/v1/staff/customers?q=morg").andExpect(jsonPath("$[0].fullName").value("Morgan Lee"));
        reception.get("/api/v1/staff/customers?q=0400 555 666").andExpect(jsonPath("$[0].id").value(customerId));
        reception.get("/api/v1/staff/customers?q={r}", reference.toLowerCase()).andExpect(jsonPath("$[0].id").value(customerId));
        reception.get("/api/v1/staff/customers?q=zz").andExpect(jsonPath("$", empty()));

        // Booking again for the same customer by id.
        book(reception, kim, "10:00", "\"customerId\": \"%s\"".formatted(customerId), false).andExpect(status().isCreated());
        reception.get("/api/v1/staff/customers/{id}", customerId)
                .andExpect(jsonPath("$.appointments.length()").value(2))
                .andExpect(jsonPath("$.noShows").value(0));
    }

    @Test
    void statusesFollowTheAllowedTransitions() throws Exception {
        String id = bookId(reception, sam, "10:00");

        reception.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"NO_SHOW\"}", id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSITION"));
        reception.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"COMPLETED\"}", id).andExpect(status().isConflict());
        reception.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"CHECKED_IN\"}", id)
                .andExpect(jsonPath("$.status").value("CHECKED_IN"));
        // Cancelling a checked-in appointment is for managers and owners.
        reception.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"CANCELLED\"}", id).andExpect(status().isConflict());
        reception.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"COMPLETED\"}", id)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.allowedTransitions", empty()));
    }

    @Test
    void cancellingRecordsWhoAndWhyAndFreesTheTime() throws Exception {
        String id = bookId(reception, sam, "10:00");
        reception.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"CANCELLED\", \"reason\": \"Called in sick\"}", id)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBy").value("staff"))
                .andExpect(jsonPath("$.cancelReason").value("Called in sick"))
                .andExpect(jsonPath("$.lateCancel").value(false));
        book(reception, sam, "10:00", NEW_CUSTOMER, false).andExpect(status().isCreated());
    }

    @Test
    void undoingANoShowFailsIfTheTimeWasRebooked() throws Exception {
        String id = bookId(reception, sam, "10:00");
        // Move it to an hour ago so a no-show is possible and today counts as the same day.
        admin.update("""
                UPDATE appointment SET start_at = now() - interval '1 hour', end_at = now(), blocked_until = now()
                WHERE id = ?::uuid""", id);
        reception.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"NO_SHOW\"}", id)
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.allowedTransitions", empty()));
        owner.get("/api/v1/staff/appointments/{id}", id).andExpect(jsonPath("$.allowedTransitions", contains("CONFIRMED")));

        admin.update("""
                INSERT INTO appointment (id, business_id, reference, location_id, customer_id, staff_id, start_at, end_at,
                                         blocked_until, source)
                SELECT gen_random_uuid(), business_id, 'REBOOKED', location_id, customer_id, staff_id, start_at, end_at,
                       blocked_until, 'staff' FROM appointment WHERE id = ?::uuid""", id);
        owner.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"CONFIRMED\"}", id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLOT_UNAVAILABLE"));
        owner.get("/api/v1/staff/appointments/{id}", id).andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void reschedulingKeepsTheStylistWhenFreeAndTheOldTimeWhenNot() throws Exception {
        String id = bookId(reception, sam, "10:00");
        String other = bookId(reception, sam, "14:00");

        // Moving by half an hour overlaps its own old time, which must not block it.
        reception.post("/api/v1/staff/appointments/{id}/reschedule", "{\"start\": \"%s\"}".formatted(at("10:30")), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffName").value("Sam"));
        // Sam is busy at 14:00, so "anyone" moves it to Kim.
        reception.post("/api/v1/staff/appointments/{id}/reschedule", "{\"start\": \"%s\"}".formatted(at("14:00")), id)
                .andExpect(jsonPath("$.staffName").value("Kim"));
        reception.post("/api/v1/staff/appointments/{id}/reschedule",
                        "{\"start\": \"%s\", \"staffId\": \"%s\"}".formatted(at("14:30"), sam), id)
                .andExpect(status().isConflict());
        reception.get("/api/v1/staff/appointments/{id}", id)
                .andExpect(jsonPath("$.startAt").value(org.hamcrest.Matchers.startsWith(at("14:00").substring(0, 16))));
        assertThat(other).isNotEqualTo(id);
    }

    @Test
    void theDayCalendarShowsHoursTimeOffAndAppointmentsPerStylist() throws Exception {
        bookId(reception, sam, "10:00");
        admin.update("""
                INSERT INTO staff_time_off (id, business_id, staff_id, kind, starts_on, ends_on, starts_at, ends_at, reason)
                VALUES (gen_random_uuid(), ?, ?, 'break', ?, ?, '12:00', '12:30', 'Lunch')""", BUSINESS_A, kim, monday, monday);
        // Sam also works at Parramatta later; that appointment shows without the customer's details.
        admin.update("INSERT INTO staff_location (business_id, staff_id, location_id) VALUES (?, ?, ?)", BUSINESS_A, sam, A_PARRAMATTA);
        admin.update("""
                INSERT INTO appointment (id, business_id, reference, location_id, customer_id, staff_id, start_at, end_at,
                                         blocked_until, source)
                SELECT gen_random_uuid(), business_id, 'ELSEWHER', ?, customer_id, staff_id, start_at + interval '6 hours',
                       end_at + interval '6 hours', blocked_until + interval '6 hours', 'staff' FROM appointment LIMIT 1""",
                A_PARRAMATTA);

        reception.get("/api/v1/staff/calendar?locationId={l}&date={d}", A_SYDNEY, monday)
                .andExpect(jsonPath("$.opening[0][0]").value("09:00:00"))
                .andExpect(jsonPath("$.columns[*].displayName", contains("Kim", "Sam")))
                .andExpect(jsonPath("$.columns[0].timeOff[0].reason").value("Lunch"))
                .andExpect(jsonPath("$.columns[1].work[0][1]").value("17:00:00"))
                .andExpect(jsonPath("$.columns[1].appointments[0].customerName").value("Pat Phone"))
                .andExpect(jsonPath("$.columns[1].appointments[0].hasNote").value(true))
                .andExpect(jsonPath("$.columns[1].appointments[1].elsewhere").value(true))
                .andExpect(jsonPath("$.columns[1].appointments[1].customerName", nullValue()));
    }

    @Test
    void stylistsSeeAndUpdateOnlyTheirOwnAppointments() throws Exception {
        // Alex is a stylist at Glow Hair; link them to Sam's profile and the Sydney branch.
        admin.update("""
                UPDATE membership SET staff_id = ? WHERE business_id = ?
                  AND user_id = (SELECT id FROM app_user WHERE email = ?)""", sam, BUSINESS_A, MULTI);
        admin.update("""
                INSERT INTO membership_location (business_id, membership_id, location_id)
                SELECT business_id, id, ? FROM membership WHERE business_id = ? AND staff_id = ?""", A_SYDNEY, BUSINESS_A, sam);
        String mine = bookId(reception, sam, "10:00");
        String theirs = bookId(reception, kim, "10:00");
        AdminClient stylist = new AdminClient(mvc, new StaffClient(mvc).androidToken(MULTI));
        stylist.post("/api/v1/auth/business", "{\"businessId\": \"%s\"}".formatted(BUSINESS_A)).andExpect(status().isOk());

        stylist.get("/api/v1/staff/agenda?date={d}", monday)
                .andExpect(jsonPath("$[*].id", contains(mine)));
        stylist.get("/api/v1/staff/appointments/{id}", theirs).andExpect(status().isNotFound());
        stylist.get("/api/v1/staff/appointments/{id}", mine)
                .andExpect(jsonPath("$.allowedTransitions", contains("CHECKED_IN")))
                .andExpect(jsonPath("$.canReschedule").value(false))
                .andExpect(jsonPath("$.internalNote").value("Prefers a quiet chair"));
        stylist.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"CANCELLED\"}", mine).andExpect(status().isConflict());
        stylist.post("/api/v1/staff/appointments/{id}/status", "{\"status\": \"CHECKED_IN\"}", mine).andExpect(status().isOk());
        stylist.put("/api/v1/staff/appointments/{id}/note", "{\"internalNote\": \"x\"}", mine).andExpect(status().isForbidden());
        book(stylist, sam, "15:00", NEW_CUSTOMER, false).andExpect(status().isForbidden());
        stylist.get("/api/v1/staff/customers?q=pat").andExpect(status().isForbidden());
        stylist.get("/api/v1/staff/calendar?locationId={l}&date={d}", A_SYDNEY, monday)
                .andExpect(jsonPath("$.columns[*].displayName", contains("Sam")));
    }

    @Test
    void receptionWorksOnlyAtTheirOwnBranch() throws Exception {
        reception.post("/api/v1/staff/appointments", booking(A_PARRAMATTA, null, at("10:00"), NEW_CUSTOMER, false)
                        .replace("%CUT%", cut.toString()))
                .andExpect(status().isForbidden());
        reception.get("/api/v1/staff/calendar?locationId={l}&date={d}", A_PARRAMATTA, monday).andExpect(status().isNotFound());
        owner.get("/api/v1/staff/calendar?locationId={l}&date={d}", A_PARRAMATTA, monday)
                .andExpect(jsonPath("$.columns", empty()))
                .andExpect(jsonPath("$.closed").value(false));
        owner.get("/api/v1/staff/agenda?date={d}", monday).andExpect(jsonPath("$", empty()));
    }

    @Test
    void theWeekRunsMondayToSundayWithEachDaysAppointments() throws Exception {
        bookId(reception, sam, "10:00");
        reception.get("/api/v1/staff/calendar/week?locationId={l}&date={d}", A_SYDNEY, monday.plusDays(3))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].date").value(monday.toString()))
                .andExpect(jsonPath("$[0].columns[1].appointments[0].customerName").value("Pat Phone"))
                .andExpect(jsonPath("$[1].opening", empty()))
                .andExpect(jsonPath("$[6].date").value(monday.plusDays(6).toString()));
    }
}
