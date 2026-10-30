package com.salonbooking.notification.app;

import com.salonbooking.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues customer emails inside the caller's transaction (NTF-4): they exist exactly when the
 * booking change commits. Customers without an email address get none.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationQueue {

    private final JdbcClient jdbc;

    NotificationQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code eventKey} makes the event unique, e.g. the new start time of a reschedule. */
    public void queue(UUID appointmentId, NotificationType type, String eventKey) {
        UUID businessId = TenantContext.requireBusinessId();
        Optional<String> email = jdbc.sql("""
                        SELECT c.email FROM appointment a JOIN customer c ON c.id = a.customer_id
                        WHERE a.business_id = :businessId AND a.id = :id AND c.email IS NOT NULL""")
                .param("businessId", businessId).param("id", appointmentId)
                .query(String.class).optional();
        if (email.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO notification (id, business_id, appointment_id, type, recipient, dedupe_key)
                        VALUES (:id, :businessId, :appointmentId, :type, :recipient, :key)
                        ON CONFLICT (business_id, dedupe_key) DO NOTHING""")
                .param("id", UUID.randomUUID()).param("businessId", businessId).param("appointmentId", appointmentId)
                .param("type", type.db()).param("recipient", email.get())
                .param("key", "%s:%s:%s".formatted(type.db(), appointmentId, eventKey))
                .update();
    }

    public void queue(UUID appointmentId, NotificationType type) {
        queue(appointmentId, type, "once");
    }

    /** A reminder queued for the old time no longer applies once the appointment moves or ends. */
    public void dropPendingReminders(UUID appointmentId) {
        jdbc.sql("""
                        UPDATE notification SET status = 'skipped', last_error = 'Appointment changed', updated_at = now()
                        WHERE business_id = :businessId AND appointment_id = :id AND type = 'reminder' AND status = 'queued'""")
                .param("businessId", TenantContext.requireBusinessId()).param("id", appointmentId)
                .update();
    }
}
