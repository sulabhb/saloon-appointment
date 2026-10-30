package com.salonbooking.booking.app;

import com.salonbooking.booking.app.AvailabilityService.Request;
import com.salonbooking.notification.app.NotificationQueue;
import com.salonbooking.notification.app.NotificationType;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.tenancy.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves a confirmed appointment to a new start, for staff (DSH-11) and customers (MGT-4). The new
 * time is re-checked; the original time stands if no stylist can take the new one.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class AppointmentMover {

    private final JdbcClient jdbc;
    private final AvailabilityService availability;
    private final NotificationQueue notifications;

    AppointmentMover(JdbcClient jdbc, AvailabilityService availability, NotificationQueue notifications) {
        this.jdbc = jdbc;
        this.availability = availability;
        this.notifications = notifications;
    }

    /** The appointment's services in booking order, for building the availability request. */
    List<UUID> serviceIds(UUID appointmentId) {
        return jdbc.sql("""
                        SELECT service_id FROM appointment_service
                        WHERE business_id = :businessId AND appointment_id = :id ORDER BY position""")
                .param("businessId", TenantContext.requireBusinessId()).param("id", appointmentId)
                .query(UUID.class).list();
    }

    /**
     * {@code request} must ignore this appointment, so it does not block its own new time. The
     * current stylist is kept when they are free. Throws SLOT_UNAVAILABLE when nobody is.
     */
    void move(UUID appointmentId, Request request, Instant start, UUID currentStaffId) {
        List<UUID> free = new ArrayList<>(availability.freeAt(request, start));
        if (free.remove(currentStaffId)) {
            free.addFirst(currentStaffId);
        }
        for (UUID candidate : free) {
            Optional<String> failure = Savepoints.attempt(jdbc, () -> jdbc.sql("""
                            UPDATE appointment SET start_at = :start, end_at = :end, blocked_until = :blocked,
                                   staff_id = :staffId, updated_at = now()
                            WHERE business_id = :businessId AND id = :id AND status = 'confirmed'""")
                    .param("start", Timestamp.from(start))
                    .param("end", Timestamp.from(start.plusSeconds(request.durationMin() * 60L)))
                    .param("blocked", Timestamp.from(start.plusSeconds(request.blockedMin() * 60L)))
                    .param("staffId", candidate)
                    .param("businessId", TenantContext.requireBusinessId()).param("id", appointmentId)
                    .update());
            if (failure.isEmpty()) {
                notifications.dropPendingReminders(appointmentId);
                notifications.queue(appointmentId, NotificationType.BOOKING_RESCHEDULED,
                        String.valueOf(start.getEpochSecond()));
                return;
            }
        }
        // Not a race in most cases: the stylist is busy, off or on a break then.
        throw new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                "That time isn't available with this stylist. Choose another time.");
    }
}
