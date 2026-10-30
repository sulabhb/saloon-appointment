package com.salonbooking.booking.app;

import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appointments for staff: one day across the branches the person can access (DSH-1). */
@Service
@Transactional(readOnly = true)
public class AgendaService {

    public record AgendaItem(UUID id, String reference, String status, Instant startAt, Instant endAt, UUID locationId,
            String locationName, String timeZone, UUID staffId, String staffName, String customerName,
            String customerPhone, String customerEmail, String services, String customerNote, String source) {}

    private final JdbcClient jdbc;

    AgendaService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The date is read in each branch's own time zone. */
    public List<AgendaItem> day(StaffPrincipal staff, LocalDate date, UUID locationId) {
        if (locationId != null && !staff.canAccessLocation(locationId)) {
            throw new NotFoundException("Branch not found.");
        }
        return jdbc.sql("""
                        SELECT a.id, a.reference, a.status, a.start_at, a.end_at, a.location_id, l.name AS location_name,
                               l.time_zone, a.staff_id, st.display_name AS staff_name, c.full_name, c.phone_e164, c.email,
                               a.customer_note, a.source,
                               (SELECT string_agg(s.name, ', ' ORDER BY s.position) FROM appointment_service s
                                 WHERE s.appointment_id = a.id) AS services
                        FROM appointment a
                        JOIN location l ON l.id = a.location_id
                        JOIN staff st ON st.id = a.staff_id
                        JOIN customer c ON c.id = a.customer_id
                        WHERE a.business_id = :businessId
                          AND (a.start_at AT TIME ZONE l.time_zone)::date = :date
                          AND (CAST(:locationId AS uuid) IS NULL OR a.location_id = :locationId)
                        ORDER BY a.start_at, st.display_name""")
                .param("businessId", TenantContext.requireBusinessId()).param("date", date)
                .param("locationId", locationId)
                .query((rs, row) -> new AgendaItem(rs.getObject("id", UUID.class), rs.getString("reference"),
                        rs.getString("status").toUpperCase(Locale.ROOT), rs.getTimestamp("start_at").toInstant(),
                        rs.getTimestamp("end_at").toInstant(), rs.getObject("location_id", UUID.class),
                        rs.getString("location_name"), rs.getString("time_zone"), rs.getObject("staff_id", UUID.class),
                        rs.getString("staff_name"), rs.getString("full_name"), rs.getString("phone_e164"),
                        rs.getString("email"), rs.getString("services"), rs.getString("customer_note"),
                        rs.getString("source")))
                .list().stream()
                .filter(a -> staff.canAccessLocation(a.locationId()))
                // Stylists see their own appointments only (requirements 4.1).
                .filter(a -> staff.role() != StaffRole.STYLIST || a.staffId().equals(staff.staffId()))
                .toList();
    }
}
