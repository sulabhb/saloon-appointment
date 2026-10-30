package com.salonbooking.notification.app;

import com.salonbooking.tenancy.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Works through the outbox for every business. Rows are claimed with SKIP LOCKED, so several
 * instances can run this at once without sending anything twice.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH = 20;

    private record Claim(UUID id, UUID businessId, int attempts) {}

    private final JdbcClient jdbc;
    private final NotificationSender sender;
    private final NotificationProperties properties;

    NotificationDispatcher(JdbcClient jdbc, NotificationSender sender, NotificationProperties properties) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.properties = properties;
    }

    /** Sends what is due; returns how many were attempted. */
    public int dispatchDue() {
        List<Claim> claims = TenantContext.callAsSystem(() -> {
            // An instance that died mid-send leaves rows in 'sending'; give them another go.
            jdbc.sql("""
                            UPDATE notification SET status = 'queued', updated_at = now()
                            WHERE status = 'sending' AND updated_at < now() - interval '10 minutes'""")
                    .update();
            return jdbc.sql("""
                            UPDATE notification SET status = 'sending', attempts = attempts + 1, updated_at = now()
                            WHERE id IN (SELECT id FROM notification WHERE status = 'queued' AND next_attempt_at <= now()
                                         ORDER BY next_attempt_at LIMIT :batch FOR UPDATE SKIP LOCKED)
                            RETURNING id, business_id, attempts""")
                    .param("batch", BATCH)
                    .query((rs, n) -> new Claim(rs.getObject("id", UUID.class), rs.getObject("business_id", UUID.class),
                            rs.getInt("attempts")))
                    .list();
        });
        for (Claim claim : claims) {
            try {
                TenantContext.callForBusinessChecked(claim.businessId(), () -> {
                    sender.send(claim.id());
                    return null;
                });
            } catch (Exception e) {
                log.warn("Notification {} attempt {} failed: {}", claim.id(), claim.attempts(), e.getMessage());
                TenantContext.runForBusiness(claim.businessId(), () -> sender.recordFailure(claim.id(), claim.attempts(), e));
            }
        }
        return claims.size();
    }

    /**
     * Queues a reminder for confirmed appointments starting within the reminder window (NTF-3).
     * The dedupe key includes the start time, so each appointment time gets one reminder; bookings
     * made inside the window get none, as their confirmation has just arrived.
     */
    public int queueReminders() {
        Duration before = properties.reminderBefore();
        return TenantContext.callAsSystem(() -> jdbc.sql("""
                        INSERT INTO notification (id, business_id, appointment_id, type, recipient, dedupe_key)
                        SELECT gen_random_uuid(), a.business_id, a.id, 'reminder', c.email,
                               'reminder:' || a.id || ':' || extract(epoch FROM a.start_at)::bigint
                        FROM appointment a JOIN customer c ON c.id = a.customer_id
                        WHERE a.status = 'confirmed' AND c.email IS NOT NULL
                          AND a.start_at > now() AND a.start_at <= now() + make_interval(secs => :before)
                          AND a.created_at < a.start_at - make_interval(secs => :before)
                        ON CONFLICT (business_id, dedupe_key) DO NOTHING""")
                .param("before", (double) before.toSeconds())
                .update());
    }
}
