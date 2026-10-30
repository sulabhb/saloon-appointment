package com.salonbooking.notification.app;

import com.salonbooking.booking.app.BookingService;
import com.salonbooking.notification.mail.MailGateway;
import com.salonbooking.tenancy.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sends one claimed notification inside its business, or records why it failed (NTF-5). */
@Service
class NotificationSender {

    private record Pending(UUID appointmentId, NotificationType type, String recipient) {}

    private final JdbcClient jdbc;
    private final BookingService bookings;
    private final MailGateway gateway;
    private final NotificationProperties properties;
    private final Clock clock;

    NotificationSender(JdbcClient jdbc, BookingService bookings, MailGateway gateway, NotificationProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Renders and sends. A management link is created for each email (only its hash is stored),
     * so no link ever sits in the outbox. Throws if the mail service did not accept it.
     */
    @Transactional
    public void send(UUID notificationId) throws Exception {
        UUID businessId = TenantContext.requireBusinessId();
        Pending pending = jdbc.sql("""
                        SELECT appointment_id, type, recipient FROM notification
                        WHERE business_id = :businessId AND id = :id AND status = 'sending'""")
                .param("businessId", businessId).param("id", notificationId)
                .query((rs, n) -> new Pending(rs.getObject("appointment_id", UUID.class),
                        NotificationType.fromDb(rs.getString("type")), rs.getString("recipient")))
                .single();

        record Appointment(String status, String cancelledBy, EmailRenderer.Data data) {}
        Appointment a = jdbc.sql("""
                        SELECT a.status, a.cancelled_by, a.start_at, a.end_at, a.reference, b.display_name, b.slug,
                               b.accent_color, b.contact_email, b.cancel_cutoff_min, l.name AS branch_name, l.address_line1,
                               l.address_line2, l.suburb, l.state, l.postcode, l.phone, l.email AS branch_email, l.time_zone,
                               st.display_name AS staff_name, c.full_name
                        FROM appointment a
                        JOIN business b ON b.id = a.business_id
                        JOIN location l ON l.id = a.location_id
                        JOIN staff st ON st.id = a.staff_id
                        JOIN customer c ON c.id = a.customer_id
                        WHERE a.business_id = :businessId AND a.id = :id""")
                .param("businessId", businessId).param("id", pending.appointmentId())
                .query((rs, n) -> {
                    Instant start = rs.getTimestamp("start_at").toInstant();
                    String address = String.join(", ", java.util.stream.Stream.of(rs.getString("address_line1"),
                                    rs.getString("address_line2"),
                                    "%s %s %s".formatted(rs.getString("suburb"), rs.getString("state"), rs.getString("postcode")))
                            .filter(s -> s != null && !s.isBlank()).toList());
                    String replyTo = rs.getString("branch_email") != null ? rs.getString("branch_email") : rs.getString("contact_email");
                    boolean canCancel = "confirmed".equals(rs.getString("status"))
                            && clock.instant().isBefore(start.minus(Duration.ofMinutes(rs.getInt("cancel_cutoff_min"))));
                    return new Appointment(rs.getString("status"), rs.getString("cancelled_by"), new EmailRenderer.Data(
                            pending.type(), rs.getString("display_name"), rs.getString("slug"), rs.getString("accent_color"),
                            rs.getString("branch_name"), address, rs.getString("phone"), replyTo,
                            ZoneId.of(rs.getString("time_zone")), start, rs.getTimestamp("end_at").toInstant(), List.of(),
                            rs.getString("staff_name"), rs.getString("full_name"), rs.getString("reference"),
                            "staff".equals(rs.getString("cancelled_by")), canCancel, null,
                            "%s/book/%s".formatted(properties.linkBaseUrl(), rs.getString("slug"))));
                })
                .single();

        // Nothing to say any more: a reminder for an appointment no longer confirmed, say.
        boolean stillRelevant = switch (pending.type()) {
            case BOOKING_CANCELLED -> "cancelled".equals(a.status());
            case REMINDER, BOOKING_RESCHEDULED -> "confirmed".equals(a.status());
            case BOOKING_CONFIRMED, MANAGE_LINK -> !"cancelled".equals(a.status());
        };
        if (!stillRelevant) {
            mark(notificationId, "skipped", null, "Appointment is %s".formatted(a.status()));
            return;
        }

        List<EmailRenderer.Service> services = jdbc.sql("""
                        SELECT name, duration_min FROM appointment_service
                        WHERE business_id = :businessId AND appointment_id = :id ORDER BY position""")
                .param("businessId", businessId).param("id", pending.appointmentId())
                .query((rs, n) -> new EmailRenderer.Service(rs.getString("name"), rs.getInt("duration_min")))
                .list();
        String manageUrl = pending.type() == NotificationType.BOOKING_CANCELLED ? null
                : "%s/m/%s".formatted(properties.linkBaseUrl(), bookings.issueToken(pending.appointmentId()));
        EmailRenderer.Data d = a.data();
        EmailRenderer.Data data = new EmailRenderer.Data(d.type(), d.businessName(), d.businessSlug(), d.accentColor(),
                d.branchName(), d.branchAddress(), d.branchPhone(), d.replyTo(), d.zone(), d.start(), d.end(), services,
                d.staffName(), d.customerName(), d.reference(), d.cancelledBySalon(), d.canCancel(), manageUrl,
                d.bookAgainUrl());

        String messageId = gateway.send(EmailRenderer.render(data, properties.fromAddress(), pending.recipient()));
        mark(notificationId, "sent", messageId, null);
    }

    /** After a failed attempt: try again later with back-off (1, 2, 4, 8 minutes), then give up. */
    @Transactional
    public void recordFailure(UUID notificationId, int attempts, Exception error) {
        boolean giveUp = attempts >= properties.maxAttempts();
        String message = String.valueOf(error.getMessage());
        jdbc.sql("""
                        UPDATE notification SET status = :status, last_error = :error, updated_at = now(),
                               next_attempt_at = now() + make_interval(mins => :delay)
                        WHERE business_id = :businessId AND id = :id""")
                .param("status", giveUp ? "failed" : "queued")
                .param("error", message.length() > 500 ? message.substring(0, 500) : message)
                .param("delay", 1 << Math.min(attempts - 1, 10))
                .param("businessId", TenantContext.requireBusinessId()).param("id", notificationId)
                .update();
    }

    private void mark(UUID notificationId, String status, String messageId, String note) {
        jdbc.sql("""
                        UPDATE notification SET status = :status, provider_message_id = :messageId, last_error = :note,
                               sent_at = CASE WHEN :status = 'sent' THEN now() ELSE sent_at END, updated_at = now()
                        WHERE business_id = :businessId AND id = :id""")
                .param("status", status).param("messageId", messageId).param("note", note)
                .param("businessId", TenantContext.requireBusinessId()).param("id", notificationId)
                .update();
    }
}
