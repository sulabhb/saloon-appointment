package com.salonbooking.notification;

import static com.salonbooking.support.TestData.A_SYDNEY;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.OWNER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import com.salonbooking.notification.app.NotificationDispatcher;
import com.salonbooking.support.AdminClient;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import jakarta.mail.Multipart;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Customer emails through a real SMTP server (NTF-1..7, MGT-6). */
class NotificationEmailTest extends IntegrationTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("salon.notifications.delivery", () -> "smtp");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
        registry.add("salon.notifications.link-base-url", () -> "https://book.example.test");
    }

    static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    static final Pattern LINK = Pattern.compile("https://book\\.example\\.test/m/([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    NotificationDispatcher dispatcher;

    UUID cut;
    UUID sam;
    LocalDate monday;

    @BeforeEach
    void setUp() {
        UUID category = UUID.randomUUID();
        cut = UUID.randomUUID();
        sam = UUID.randomUUID();
        admin.update("INSERT INTO service_category (id, business_id, name) VALUES (?, ?, 'Hair')", category, BUSINESS_A);
        admin.update("""
                INSERT INTO service (id, business_id, category_id, name, default_duration_min, buffer_after_min, default_price)
                VALUES (?, ?, ?, 'Cut', 60, 0, 8500)""", cut, BUSINESS_A, category);
        admin.update("INSERT INTO location_service (business_id, location_id, service_id) VALUES (?, ?, ?)", BUSINESS_A, A_SYDNEY, cut);
        admin.update("INSERT INTO staff (id, business_id, display_name) VALUES (?, ?, 'Sam')", sam, BUSINESS_A);
        admin.update("INSERT INTO staff_location (business_id, staff_id, location_id) VALUES (?, ?, ?)", BUSINESS_A, sam, A_SYDNEY);
        admin.update("INSERT INTO staff_service (business_id, staff_id, service_id) VALUES (?, ?, ?)", BUSINESS_A, sam, cut);
        admin.update("""
                INSERT INTO staff_shift (id, business_id, staff_id, location_id, weekday, starts_at, ends_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 1, '09:00', '17:00')""", BUSINESS_A, sam, A_SYDNEY);
        admin.update("UPDATE location SET email = 'sydney@glowhair.test', phone = '02 9000 0000' WHERE id = ?", A_SYDNEY);
        monday = LocalDate.now(SYDNEY).plusDays(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    String at(String time) {
        return ZonedDateTime.of(monday, LocalTime.parse(time), SYDNEY).toInstant().toString();
    }

    ResultActions bookOnline(String time) throws Exception {
        return mvc.perform(post("/api/v1/public/b/glow-hair/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                {"locationSlug": "sydney-cbd", "serviceIds": ["%s"], "start": "%s",
                 "customer": {"fullName": "Jamie Customer", "email": "jamie@example.test", "phone": "0412 345 678"},
                 "acceptsPolicy": true, "idempotencyKey": "%s"}""".formatted(cut, at(time), UUID.randomUUID())));
    }

    static String textOf(MimeMessage message) throws Exception {
        Multipart mixed = (Multipart) message.getContent();
        StringBuilder all = new StringBuilder();
        collect(mixed, all);
        return all.toString();
    }

    private static void collect(Multipart part, StringBuilder into) throws Exception {
        for (int i = 0; i < part.getCount(); i++) {
            Object content = part.getBodyPart(i).getContent();
            if (content instanceof Multipart nested) {
                collect(nested, into);
            } else if (content instanceof String text) {
                into.append(text).append('\n');
            } else {
                into.append("[attachment ").append(part.getBodyPart(i).getFileName()).append("]\n");
            }
        }
    }

    static String token(MimeMessage message) throws Exception {
        Matcher m = LINK.matcher(textOf(message));
        assertThat(m.find()).as("management link in email").isTrue();
        return m.group(1);
    }

    @Test
    void anOnlineBookingSendsAConfirmationWithAWorkingLinkAndCalendarFile() throws Exception {
        String reference = JsonPath.read(bookOnline("10:00").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.booking.reference");
        assertThat(smtp.getReceivedMessages()).isEmpty();

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage email = received[0];
        assertThat(email.getSubject()).startsWith("Booking confirmed: ");
        assertThat(((InternetAddress) email.getFrom()[0]).getPersonal()).isEqualTo("Glow Hair");
        assertThat(((InternetAddress) email.getFrom()[0]).getAddress()).isEqualTo("bookings@salonplatform.test");
        assertThat(((InternetAddress) email.getReplyTo()[0]).getAddress()).isEqualTo("sydney@glowhair.test");
        String text = textOf(email);
        assertThat(text).contains(reference, "Cut", "With: Sam", "02 9000 0000", "[attachment booking-" + reference + ".ics]");
        assertThat(text).doesNotContain("payment", "pay ");

        mvc.perform(get("/api/v1/public/manage/{token}", token(email))).andExpect(jsonPath("$.reference").value(reference));
        assertThat(admin.queryForObject("SELECT status FROM notification", String.class)).isEqualTo("sent");
        // Nothing is sent twice.
        assertThat(dispatcher.dispatchDue()).isZero();
    }

    @Test
    void cancellationsAndMovesAreEmailedAndWalkInsAreNot() throws Exception {
        String token = JsonPath.read(bookOnline("10:00").andReturn().getResponse().getContentAsString(), "$.manageToken");
        mvc.perform(post("/api/v1/public/manage/{token}/cancel", token)).andExpect(status().isOk());
        dispatcher.dispatchDue();
        // Cancelled before the confirmation went out: the customer gets only the cancellation.
        assertThat(smtp.getReceivedMessages()).extracting(MimeMessage::getSubject)
                .singleElement().satisfies(s -> assertThat(s).startsWith("Booking cancelled: "));
        assertThat(textOf(smtp.getReceivedMessages()[0]))
                .contains("cancelled as you asked", "Book again: https://book.example.test/book/glow-hair");
        assertThat(admin.queryForList("SELECT status FROM notification ORDER BY created_at", String.class))
                .containsExactly("skipped", "sent");

        AdminClient owner = new AdminClient(mvc, new StaffClient(mvc).androidToken(OWNER_A));
        String id = JsonPath.read(owner.post("/api/v1/staff/appointments", """
                        {"locationId": "%s", "serviceIds": ["%s"], "start": "%s", "walkIn": false, "idempotencyKey": "%s",
                         "customer": {"fullName": "Phone Person", "email": "phone@example.test"}}"""
                        .formatted(A_SYDNEY, cut, at("12:00"), UUID.randomUUID()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        owner.post("/api/v1/staff/appointments/{id}/reschedule", "{\"start\": \"%s\"}".formatted(at("14:00")), id)
                .andExpect(status().isOk());
        owner.post("/api/v1/staff/appointments", """
                {"locationId": "%s", "serviceIds": ["%s"], "start": "%s", "walkIn": true, "idempotencyKey": "%s",
                 "customer": {"fullName": "Walk In", "email": "walkin@example.test"}}""".formatted(A_SYDNEY, cut, at("16:00"), UUID.randomUUID()))
                .andExpect(status().isCreated());
        dispatcher.dispatchDue();

        assertThat(smtp.getReceivedMessages()).extracting(m -> m.getAllRecipients()[0].toString() + " " + m.getSubject())
                .anySatisfy(s -> assertThat(s).startsWith("phone@example.test Booking confirmed"))
                .anySatisfy(s -> assertThat(s).startsWith("phone@example.test Booking moved"))
                .noneSatisfy(s -> assertThat(s).startsWith("walkin@"));
        owner.get("/api/v1/staff/appointments/{id}", id)
                .andExpect(jsonPath("$.emails[*].type", org.hamcrest.Matchers.contains("booking_confirmed", "booking_rescheduled")))
                .andExpect(jsonPath("$.emails[*].status", org.hamcrest.Matchers.contains("sent", "sent")));
    }

    @Test
    void failedSendsAreRetriedThenShownToStaff() throws Exception {
        String reference = JsonPath.read(bookOnline("10:00").andReturn().getResponse().getContentAsString(), "$.booking.reference");
        smtp.stop();

        dispatcher.dispatchDue();
        assertThat(admin.queryForMap("SELECT status, attempts, last_error FROM notification"))
                .containsEntry("status", "queued").containsEntry("attempts", 1);
        assertThat(admin.queryForObject("SELECT next_attempt_at > now() FROM notification", Boolean.class)).isTrue();
        // No management link is left behind by a failed attempt: only the one from the booking response.
        assertThat(admin.queryForObject("SELECT count(*) FROM booking_token", Integer.class)).isEqualTo(1);

        for (int i = 2; i <= 5; i++) {
            admin.update("UPDATE notification SET next_attempt_at = now()");
            dispatcher.dispatchDue();
        }
        assertThat(admin.queryForMap("SELECT status, attempts FROM notification"))
                .containsEntry("status", "failed").containsEntry("attempts", 5);

        AdminClient owner = new AdminClient(mvc, new StaffClient(mvc).androidToken(OWNER_A));
        String id = admin.queryForObject("SELECT id::text FROM appointment WHERE reference = ?", String.class, reference);
        owner.get("/api/v1/staff/appointments/{id}", id)
                .andExpect(jsonPath("$.emails[0].status").value("failed"))
                .andExpect(jsonPath("$.emails[0].lastError").isString());
        smtp.start();
    }

    @Test
    void oneReminderIsSentShortlyBeforeTheAppointment() throws Exception {
        bookOnline("10:00").andExpect(status().isCreated());
        dispatcher.dispatchDue();
        assertThat(dispatcher.queueReminders()).as("too far ahead").isZero();

        admin.update("""
                UPDATE appointment SET start_at = now() + interval '20 hours', end_at = now() + interval '21 hours',
                       blocked_until = now() + interval '21 hours', created_at = now() - interval '3 days'""");
        assertThat(dispatcher.queueReminders()).isEqualTo(1);
        assertThat(dispatcher.queueReminders()).as("only one per appointment").isZero();
        dispatcher.dispatchDue();
        assertThat(smtp.getReceivedMessages()).extracting(MimeMessage::getSubject)
                .anySatisfy(s -> assertThat(s).startsWith("Reminder: "));
    }

    @Test
    void aLostLinkIsReplacedOnlyWhenReferenceAndEmailMatch() throws Exception {
        String created = bookOnline("10:00").andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(created, "$.booking.reference");
        String oldToken = JsonPath.read(created, "$.manageToken");
        dispatcher.dispatchDue();
        int before = smtp.getReceivedMessages().length;

        String wrong = mvc.perform(post("/api/v1/public/b/glow-hair/manage/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\": \"%s\", \"email\": \"someone@else.test\"}".formatted(reference)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String right = mvc.perform(post("/api/v1/public/b/glow-hair/manage/recover").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\": \"%s\", \"email\": \"JAMIE@example.test\"}".formatted(reference.toLowerCase())))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(right).isEqualTo(wrong);

        mvc.perform(get("/api/v1/public/manage/{token}", oldToken)).andExpect(status().isNotFound());
        dispatcher.dispatchDue();
        MimeMessage[] received = smtp.getReceivedMessages();
        assertThat(received).hasSize(before + 1);
        MimeMessage email = received[received.length - 1];
        assertThat(email.getSubject()).isEqualTo("Your booking link for Glow Hair");
        mvc.perform(get("/api/v1/public/manage/{token}", token(email))).andExpect(status().isOk());
    }
}
