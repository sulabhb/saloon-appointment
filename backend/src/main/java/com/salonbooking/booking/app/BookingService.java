package com.salonbooking.booking.app;

import com.salonbooking.booking.app.AvailabilityService.Request;
import com.salonbooking.booking.app.AvailabilityService.ResolvedService;
import com.salonbooking.booking.domain.Contacts;
import com.salonbooking.identity.app.SessionTokens;
import com.salonbooking.notification.app.NotificationQueue;
import com.salonbooking.notification.app.NotificationType;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, viewing and cancelling bookings (BKG-9..13, BKG-16, MGT-1..3). A booking is created in
 * one transaction; the database's no-overlap constraint is the final guard against double booking.
 */
@Service
public class BookingService {

    public record NewBooking(String locationSlug, List<UUID> serviceIds, UUID staffId, Instant start, String fullName,
            String email, String phone, String note, UUID idempotencyKey, String source) {}

    public record BookedService(String name, int durationMin, long priceCents, boolean priceIsFrom) {}

    public record BranchContact(String name, String addressLine1, String addressLine2, String suburb, String state,
            String postcode, String phone, String timeZone) {}

    /** What the customer sees after booking and through their management link. Contacts are masked (MGT-2). */
    public record BookingView(String reference, String status, String businessName, String businessSlug,
            String accentColor, BranchContact branch, Instant startAt, Instant endAt, List<BookedService> services,
            long totalPriceCents, String staffName, String customerName, String email, String phone, boolean canCancel,
            Instant cancelBefore) {}

    /**
     * A booking made by staff at the desk or on the phone (DSH-9) or a walk-in (DSH-10). Either
     * {@code customerId} names an existing customer, or the name and contacts describe a new one.
     */
    public record StaffBooking(UUID locationId, List<UUID> serviceIds, UUID staffId, Instant start, UUID customerId,
            String fullName, String email, String phone, String note, String internalNote, boolean walkIn,
            UUID idempotencyKey, UUID createdBy) {}

    /** What is being inserted, whoever books it. */
    private record Insert(Request request, Instant start, UUID idempotencyKey, String source, String status, String note,
            String internalNote, UUID createdBy) {}

    /** The management token is only ever returned here, once. */
    public record Created(BookingView booking, String manageToken) {}

    public record TokenTarget(UUID businessId, UUID appointmentId) {}

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration LINK_LIFETIME_AFTER_END = Duration.ofDays(30);

    private final JdbcClient jdbc;
    private final AvailabilityService availability;
    private final NotificationQueue notifications;
    private final AppointmentMover mover;
    private final Clock clock;

    BookingService(JdbcClient jdbc, AvailabilityService availability, NotificationQueue notifications,
            AppointmentMover mover, Clock clock) {
        this.jdbc = jdbc;
        this.availability = availability;
        this.notifications = notifications;
        this.mover = mover;
        this.clock = clock;
    }

    @Transactional
    public Created create(NewBooking booking) {
        Optional<UUID> existing = byIdempotencyKey(booking.idempotencyKey());
        if (existing.isPresent()) {
            return new Created(view(existing.get()), issueToken(existing.get()));
        }
        String email = Contacts.email(booking.email());
        String phone = Contacts.phone(booking.phone())
                .orElseThrow(() -> invalid("Enter a mobile number, for example 0412 345 678."));
        Request request = availability.request(booking.locationSlug(), booking.serviceIds(), booking.staffId());
        UUID id = insertBooking(new Insert(request, booking.start(), booking.idempotencyKey(), booking.source(), "confirmed",
                booking.note(), null, null), () -> {
                    requireUnderLimit(email, phone);
                    return customer(booking.fullName().trim(), email, phone);
                });
        notifications.queue(id, NotificationType.BOOKING_CONFIRMED);
        return new Created(view(id), issueToken(id));
    }

    /**
     * Staff bookings skip the online limits (notice, horizon, bookings per contact) but never
     * double book (D-12). Walk-ins start checked in and may leave out contact details (D-4).
     */
    @Transactional
    public UUID createByStaff(StaffBooking booking) {
        Optional<UUID> existing = byIdempotencyKey(booking.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }
        Request request = availability.staffRequest(booking.locationId(), booking.serviceIds(), booking.staffId());
        UUID id = insertBooking(new Insert(request, booking.start(), booking.idempotencyKey(),
                booking.walkIn() ? "walk_in" : "staff", booking.walkIn() ? "checked_in" : "confirmed", booking.note(),
                booking.internalNote(), booking.createdBy()), () -> staffCustomer(booking));
        // A walk-in is already at the salon; phone bookings get the same confirmation as online ones.
        if (!booking.walkIn()) {
            notifications.queue(id, NotificationType.BOOKING_CONFIRMED);
        }
        return id;
    }

    /** Re-checks the time, then inserts with each free stylist in turn until one sticks (HLD 8.2). */
    private UUID insertBooking(Insert insert, java.util.function.Supplier<UUID> customer) {
        UUID businessId = business();
        List<UUID> free = availability.freeAt(insert.request(), insert.start());
        if (free.isEmpty()) {
            throw slotGone();
        }
        UUID customerId = customer.get();
        for (UUID staffId : assignmentOrder(free, insert.start(), insert.request().branch().zone())) {
            Optional<UUID> created = tryInsert(businessId, insert, staffId, customerId);
            if (created.isPresent()) {
                insertServices(businessId, created.get(), insert.request().services());
                return created.get();
            }
            // Someone booked this stylist a moment ago; another one may still be free.
            Optional<UUID> raced = byIdempotencyKey(insert.idempotencyKey());
            if (raced.isPresent()) {
                return raced.get();
            }
        }
        throw slotGone();
    }

    private UUID staffCustomer(StaffBooking booking) {
        if (booking.customerId() != null) {
            boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM customer WHERE business_id = :businessId AND id = :id)")
                    .param("businessId", business()).param("id", booking.customerId()).query(Boolean.class).single();
            if (!exists) {
                throw new NotFoundException("Customer not found.");
            }
            return booking.customerId();
        }
        if (booking.fullName() == null || booking.fullName().isBlank()) {
            throw invalid("Enter the customer's name.");
        }
        String email = booking.email() == null || booking.email().isBlank() ? null : Contacts.email(booking.email());
        String phone = null;
        if (booking.phone() != null && !booking.phone().isBlank()) {
            phone = Contacts.phone(booking.phone())
                    .orElseThrow(() -> invalid("Enter a mobile number, for example 0412 345 678."));
        }
        if (!booking.walkIn() && email == null && phone == null) {
            throw invalid("Add a mobile or email so the salon can reach the customer.");
        }
        return customer(booking.fullName().trim(), email, phone);
    }

    /** Finds the appointment a management token opens. Runs outside any business, so not transactional. */
    public Optional<TokenTarget> resolve(String token) {
        return TenantContext.callAsSystem(() -> jdbc.sql("""
                        SELECT business_id, appointment_id FROM booking_token
                        WHERE token_hash = :hash AND revoked_at IS NULL AND expires_at > now()""")
                .param("hash", SessionTokens.hash(token))
                .query((rs, row) -> new TokenTarget(rs.getObject("business_id", UUID.class),
                        rs.getObject("appointment_id", UUID.class)))
                .optional());
    }

    @Transactional(readOnly = true)
    public BookingView view(UUID appointmentId) {
        UUID businessId = business();
        BookingView base = jdbc.sql("""
                        SELECT a.reference, a.status, a.start_at, a.end_at, b.display_name, b.slug, b.accent_color,
                               b.cancel_cutoff_min, l.name AS location_name, l.address_line1, l.address_line2, l.suburb,
                               l.state, l.postcode, l.phone AS location_phone, l.time_zone, st.display_name AS staff_name,
                               c.full_name, c.email, c.phone_e164
                        FROM appointment a
                        JOIN business b ON b.id = a.business_id
                        JOIN location l ON l.id = a.location_id
                        JOIN staff st ON st.id = a.staff_id
                        JOIN customer c ON c.id = a.customer_id
                        WHERE a.business_id = :businessId AND a.id = :id""")
                .param("businessId", businessId).param("id", appointmentId)
                .query((rs, row) -> {
                    Instant start = rs.getTimestamp("start_at").toInstant();
                    Instant cancelBefore = start.minusSeconds(rs.getInt("cancel_cutoff_min") * 60L);
                    String status = rs.getString("status");
                    return new BookingView(rs.getString("reference"), status.toUpperCase(java.util.Locale.ROOT),
                            rs.getString("display_name"), rs.getString("slug"), rs.getString("accent_color"),
                            new BranchContact(rs.getString("location_name"), rs.getString("address_line1"),
                                    rs.getString("address_line2"), rs.getString("suburb"), rs.getString("state"),
                                    rs.getString("postcode"), rs.getString("location_phone"), rs.getString("time_zone")),
                            start, rs.getTimestamp("end_at").toInstant(), List.of(), 0, rs.getString("staff_name"),
                            rs.getString("full_name"),
                            rs.getString("email") == null ? null : Contacts.maskEmail(rs.getString("email")),
                            rs.getString("phone_e164") == null ? null : Contacts.maskPhone(rs.getString("phone_e164")),
                            "confirmed".equals(status) && clock.instant().isBefore(cancelBefore), cancelBefore);
                })
                .optional()
                .orElseThrow(() -> new NotFoundException("Booking not found."));
        List<BookedService> services = jdbc.sql("""
                        SELECT name, duration_min, price, price_is_from FROM appointment_service
                        WHERE business_id = :businessId AND appointment_id = :id ORDER BY position""")
                .param("businessId", businessId).param("id", appointmentId)
                .query((rs, row) -> new BookedService(rs.getString("name"), rs.getInt("duration_min"),
                        rs.getLong("price"), rs.getBoolean("price_is_from")))
                .list();
        return new BookingView(base.reference(), base.status(), base.businessName(), base.businessSlug(),
                base.accentColor(), base.branch(), base.startAt(), base.endAt(), services,
                services.stream().mapToLong(BookedService::priceCents).sum(), base.staffName(), base.customerName(),
                base.email(), base.phone(), base.canCancel(), base.cancelBefore());
    }

    /** Customer cancellation through the management link, until the cut-off (MGT-3). */
    @Transactional
    public BookingView cancelByCustomer(UUID appointmentId) {
        BookingView current = view(appointmentId);
        if (!current.canCancel()) {
            throw new ApiException(HttpStatus.CONFLICT, "POLICY_CUTOFF_PASSED", "CANCELLED".equals(current.status())
                    ? "This booking is already cancelled."
                    : "It's too late to cancel online. Please call the salon.");
        }
        jdbc.sql("""
                        UPDATE appointment SET status = 'cancelled', cancelled_at = now(), cancelled_by = 'customer',
                               updated_at = now()
                        WHERE business_id = :businessId AND id = :id AND status = 'confirmed'""")
                .param("businessId", business()).param("id", appointmentId)
                .update();
        notifications.queue(appointmentId, NotificationType.BOOKING_CANCELLED);
        notifications.dropPendingReminders(appointmentId);
        return view(appointmentId);
    }

    /**
     * What a customer can move their booking to (MGT-4): the online rules apply (notice, horizon,
     * stylists bookable online), and the booking does not block its own new time.
     */
    @Transactional(readOnly = true)
    public Request rescheduleRequest(UUID appointmentId, UUID staffId) {
        String locationSlug = jdbc.sql("""
                        SELECT l.slug FROM appointment a JOIN location l ON l.id = a.location_id
                        WHERE a.business_id = :businessId AND a.id = :id""")
                .param("businessId", business()).param("id", appointmentId)
                .query(String.class).single();
        return availability.request(locationSlug, mover.serviceIds(appointmentId), staffId).ignoring(appointmentId);
    }

    public record Stylist(UUID id, String displayName) {}

    /** Who a customer may move to: stylists bookable online who do every service on the booking. */
    public record RescheduleOptions(UUID currentStaffId, List<Stylist> stylists) {}

    @Transactional(readOnly = true)
    public RescheduleOptions rescheduleOptions(UUID appointmentId) {
        List<UUID> qualified = rescheduleRequest(appointmentId, null).candidates();
        List<Stylist> stylists = jdbc.sql("""
                        SELECT id, display_name FROM staff WHERE business_id = :businessId AND id IN (:ids)
                        ORDER BY display_name""")
                .param("businessId", business()).param("ids", qualified)
                .query((rs, n) -> new Stylist(rs.getObject("id", UUID.class), rs.getString("display_name")))
                .list();
        return new RescheduleOptions(currentStaff(appointmentId), stylists);
    }

    private UUID currentStaff(UUID appointmentId) {
        return jdbc.sql("SELECT staff_id FROM appointment WHERE business_id = :businessId AND id = :id")
                .param("businessId", business()).param("id", appointmentId)
                .query(UUID.class).single();
    }

    /** Customer reschedule through the management link, until the same cut-off as cancelling (MGT-4). */
    @Transactional
    public BookingView rescheduleByCustomer(UUID appointmentId, Instant start, UUID staffId) {
        BookingView current = view(appointmentId);
        if (!current.canCancel()) {
            throw new ApiException(HttpStatus.CONFLICT, "POLICY_CUTOFF_PASSED", "CANCELLED".equals(current.status())
                    ? "This booking is cancelled. Please make a new booking."
                    : "It's too late to change this booking online. Please call the salon.");
        }
        mover.move(appointmentId, rescheduleRequest(appointmentId, staffId), start, currentStaff(appointmentId));
        return view(appointmentId);
    }

    /**
     * A lost link (MGT-6): when the reference and email match an appointment that is still
     * relevant, earlier links stop working and a new one is emailed to the address on file.
     * Callers get the same answer either way.
     */
    @Transactional
    public void recoverLink(String reference, String email) {
        Optional<UUID> match = jdbc.sql("""
                        SELECT a.id FROM appointment a JOIN customer c ON c.id = a.customer_id
                        WHERE a.business_id = :businessId AND a.reference = upper(trim(:reference))
                          AND c.email = :email AND a.status <> 'cancelled' AND a.end_at > now() - interval '30 days'""")
                .param("businessId", business()).param("reference", reference).param("email", Contacts.email(email))
                .query(UUID.class).optional();
        match.ifPresent(id -> {
            jdbc.sql("UPDATE booking_token SET revoked_at = now() WHERE business_id = :businessId AND appointment_id = :id AND revoked_at IS NULL")
                    .param("businessId", business()).param("id", id).update();
            notifications.queue(id, NotificationType.MANAGE_LINK, UUID.randomUUID().toString());
        });
    }

    // ---- Steps ----------------------------------------------------------------------------------

    private Optional<UUID> byIdempotencyKey(UUID key) {
        return jdbc.sql("SELECT id FROM appointment WHERE business_id = :businessId AND idempotency_key = :key")
                .param("businessId", business()).param("key", key)
                .query(UUID.class)
                .optional();
    }

    /** One contact can hold a limited number of future online bookings (BKG-16). */
    private void requireUnderLimit(String email, String phone) {
        int limit = jdbc.sql("SELECT max_future_bookings_per_contact FROM business WHERE id = :id")
                .param("id", business()).query(Integer.class).single();
        int held = jdbc.sql("""
                        SELECT count(*) FROM appointment a JOIN customer c ON c.id = a.customer_id
                        WHERE a.business_id = :businessId AND a.status = 'confirmed' AND a.start_at > now()
                          AND (c.email = :email OR c.phone_e164 = :phone)""")
                .param("businessId", business()).param("email", email).param("phone", phone)
                .query(Integer.class).single();
        if (held >= limit) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_LIMIT_REACHED",
                    "You already have %d upcoming bookings here. Change or cancel one, or call the salon.".formatted(held));
        }
    }

    /** Exact email match, then exact mobile match, never name (D-5). Missing contact details are filled in. */
    private UUID customer(String fullName, String email, String phone) {
        UUID businessId = business();
        Optional<UUID> byEmail = email == null ? Optional.empty()
                : jdbc.sql("SELECT id FROM customer WHERE business_id = :businessId AND email = :email")
                        .param("businessId", businessId).param("email", email).query(UUID.class).optional();
        Optional<UUID> match = byEmail.or(() -> phone == null ? Optional.empty() : jdbc.sql("""
                        SELECT id FROM customer WHERE business_id = :businessId AND phone_e164 = :phone
                        ORDER BY created_at LIMIT 1""")
                .param("businessId", businessId).param("phone", phone).query(UUID.class).optional());
        if (match.isPresent()) {
            jdbc.sql("""
                            UPDATE customer SET email = coalesce(email, CAST(:email AS citext)),
                                   phone_e164 = coalesce(phone_e164, CAST(:phone AS text)), updated_at = now()
                            WHERE business_id = :businessId AND id = :id
                              AND NOT EXISTS (SELECT 1 FROM customer o WHERE o.business_id = :businessId
                                                AND o.email = CAST(:email AS citext) AND o.id <> :id)""")
                    .param("businessId", businessId).param("id", match.get()).param("email", email).param("phone", phone)
                    .update();
            return match.get();
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO customer (id, business_id, full_name, email, phone_e164)
                        VALUES (:id, :businessId, :name, :email, :phone)""")
                .param("id", id).param("businessId", businessId).param("name", fullName).param("email", email)
                .param("phone", phone)
                .update();
        return id;
    }

    /** "Any stylist": fewest booked minutes that day first, ties in random order (D-10). */
    private List<UUID> assignmentOrder(List<UUID> free, Instant start, ZoneId zone) {
        if (free.size() == 1) {
            return free;
        }
        LocalDate day = start.atZone(zone).toLocalDate();
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        Map<UUID, Long> minutes = new HashMap<>();
        jdbc.sql("""
                        SELECT staff_id, sum(extract(epoch FROM (end_at - start_at)) / 60)::bigint AS minutes FROM appointment
                        WHERE business_id = :businessId AND staff_id IN (:staff) AND status = 'confirmed'
                          AND start_at >= :from AND start_at < :to
                        GROUP BY staff_id""")
                .param("businessId", business()).param("staff", free)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query(rs -> {
                    minutes.put(rs.getObject("staff_id", UUID.class), rs.getLong("minutes"));
                });
        Map<UUID, Integer> tieBreak = new HashMap<>();
        free.forEach(id -> tieBreak.put(id, RANDOM.nextInt()));
        List<UUID> ordered = new ArrayList<>(free);
        ordered.sort(Comparator.<UUID>comparingLong(id -> minutes.getOrDefault(id, 0L)).thenComparingInt(tieBreak::get));
        return ordered;
    }

    /**
     * Inserts the appointment inside a savepoint. Returns empty if the stylist was booked in the
     * meantime (exclusion constraint) or the idempotency key was used concurrently.
     */
    private Optional<UUID> tryInsert(UUID businessId, Insert insert, UUID staffId, UUID customerId) {
        Request request = insert.request();
        Instant end = insert.start().plusSeconds(request.durationMin() * 60L);
        Instant blockedUntil = insert.start().plusSeconds(request.blockedMin() * 60L);
        for (int attempt = 0; attempt < 5; attempt++) {
            UUID id = UUID.randomUUID();
            Optional<String> failure = Savepoints.attempt(jdbc, () -> jdbc.sql("""
                            INSERT INTO appointment (id, business_id, reference, location_id, customer_id, staff_id, start_at,
                                                     end_at, blocked_until, status, source, customer_note, internal_note,
                                                     idempotency_key, created_by_user_id, status_changed_at)
                            VALUES (:id, :businessId, :reference, :locationId, :customerId, :staffId, :start, :end,
                                    :blockedUntil, :status, :source, :note, :internalNote, :key, :createdBy,
                                    CASE WHEN :status = 'confirmed' THEN NULL ELSE now() END)""")
                    .param("id", id).param("businessId", businessId).param("reference", newReference())
                    .param("locationId", request.branch().id()).param("customerId", customerId)
                    .param("staffId", staffId).param("start", Timestamp.from(insert.start()))
                    .param("end", Timestamp.from(end)).param("blockedUntil", Timestamp.from(blockedUntil))
                    .param("status", insert.status()).param("source", insert.source())
                    .param("note", blankToNull(insert.note())).param("internalNote", blankToNull(insert.internalNote()))
                    .param("key", insert.idempotencyKey()).param("createdBy", insert.createdBy())
                    .update());
            if (failure.isEmpty()) {
                return Optional.of(id);
            }
            if (!failure.get().equals(Savepoints.REFERENCE_TAKEN)) {
                return Optional.empty();
            }
        }
        throw new IllegalStateException("Could not find a free booking reference");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void insertServices(UUID businessId, UUID appointmentId, List<ResolvedService> services) {
        for (int i = 0; i < services.size(); i++) {
            ResolvedService s = services.get(i);
            jdbc.sql("""
                            INSERT INTO appointment_service (business_id, appointment_id, position, service_id, name,
                                                             duration_min, price, price_is_from)
                            VALUES (:businessId, :appointmentId, :position, :serviceId, :name, :duration, :price, :from)""")
                    .param("businessId", businessId).param("appointmentId", appointmentId).param("position", i)
                    .param("serviceId", s.id()).param("name", s.name()).param("duration", s.durationMin())
                    .param("price", s.priceCents()).param("from", s.priceIsFrom())
                    .update();
        }
    }

    /** A new management link; earlier links keep working (BKG-10 retries and each email get their own). */
    public String issueToken(UUID appointmentId) {
        String token = SessionTokens.newToken();
        jdbc.sql("""
                        INSERT INTO booking_token (id, business_id, appointment_id, token_hash, expires_at)
                        SELECT :id, business_id, id, :hash, end_at + make_interval(days => :days)
                        FROM appointment WHERE business_id = :businessId AND id = :appointmentId""")
                .param("id", UUID.randomUUID()).param("hash", SessionTokens.hash(token))
                .param("days", (int) LINK_LIFETIME_AFTER_END.toDays())
                .param("businessId", business()).param("appointmentId", appointmentId)
                .update();
        return token;
    }

    private static String newReference() {
        StringBuilder reference = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            reference.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
        }
        return reference.toString();
    }

    private static UUID business() {
        return TenantContext.requireBusinessId();
    }

    static ApiException slotGone() {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                "Sorry, that time was just taken. Please choose another time.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
