package com.salonbooking.staff.app;

import com.salonbooking.identity.app.AdminAccess;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff profiles: the people customers can book (STF-1..3, STF-8). A profile is separate from
 * sign-in; a stylist may never sign in, and a receptionist signs in without a profile.
 */
@Service
@Transactional
public class StaffAdminService {

    public record StaffInput(String displayName, String bio, boolean bookableOnline, List<UUID> locationIds,
            List<UUID> serviceIds) {}

    public enum LoginStatus { ACTIVE, INVITED }

    public record LoginView(String email, StaffRole role, LoginStatus status) {}

    public record StaffView(UUID id, String displayName, String bio, boolean bookableOnline, boolean active,
            List<UUID> locationIds, List<UUID> serviceIds, LoginView login) {}

    private final JdbcClient jdbc;

    StaffAdminService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<StaffView> list() {
        UUID businessId = business();
        Map<UUID, List<UUID>> locations = links("staff_location", "location_id", businessId);
        Map<UUID, List<UUID>> services = links("staff_service", "service_id", businessId);
        Map<UUID, LoginView> logins = logins(businessId);
        return jdbc.sql("SELECT * FROM staff WHERE business_id = :businessId ORDER BY active DESC, display_name")
                .param("businessId", businessId)
                .query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new StaffView(id, rs.getString("display_name"), rs.getString("bio"),
                            rs.getBoolean("bookable_online"), rs.getBoolean("active"),
                            locations.getOrDefault(id, List.of()), services.getOrDefault(id, List.of()), logins.get(id));
                })
                .list();
    }

    public UUID create(StaffPrincipal staff, StaffInput input) {
        AdminAccess.requireOwner(staff);
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO staff (id, business_id, display_name, bio, bookable_online)
                        VALUES (:id, :businessId, :name, :bio, :bookable)""")
                .param("id", id).param("businessId", business()).param("name", input.displayName().trim())
                .param("bio", blankToNull(input.bio())).param("bookable", input.bookableOnline())
                .update();
        replaceLinks(id, input);
        return id;
    }

    public void update(StaffPrincipal staff, UUID staffId, StaffInput input, boolean active) {
        AdminAccess.requireOwner(staff);
        int updated = jdbc.sql("""
                        UPDATE staff SET display_name = :name, bio = :bio, bookable_online = :bookable, active = :active,
                               updated_at = now()
                        WHERE business_id = :businessId AND id = :id""")
                .param("name", input.displayName().trim()).param("bio", blankToNull(input.bio()))
                .param("bookable", input.bookableOnline()).param("active", active)
                .param("businessId", business()).param("id", staffId)
                .update();
        if (updated == 0) {
            throw new NotFoundException("Staff member not found.");
        }
        replaceLinks(staffId, input);
    }

    /** Branches of a staff profile, used as the branch access of their sign-in. */
    @Transactional(readOnly = true)
    public Optional<List<UUID>> locationsOf(UUID staffId) {
        UUID businessId = business();
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM staff WHERE business_id = :businessId AND id = :id)")
                .param("businessId", businessId).param("id", staffId).query(Boolean.class).single();
        if (!exists) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("SELECT location_id FROM staff_location WHERE business_id = :businessId AND staff_id = :id")
                .param("businessId", businessId).param("id", staffId).query(UUID.class).list());
    }

    /**
     * Branches and services are only linked if they belong to this business; anything else is ignored.
     * Branch links are changed one by one, because removing one also removes the shifts there.
     */
    private void replaceLinks(UUID staffId, StaffInput input) {
        UUID businessId = business();
        List<UUID> locationIds = input.locationIds().stream().distinct().toList();
        if (locationIds.isEmpty()) {
            jdbc.sql("DELETE FROM staff_location WHERE business_id = :businessId AND staff_id = :id")
                    .param("businessId", businessId).param("id", staffId).update();
        } else {
            jdbc.sql("DELETE FROM staff_location WHERE business_id = :businessId AND staff_id = :id AND location_id NOT IN (:keep)")
                    .param("businessId", businessId).param("id", staffId).param("keep", locationIds).update();
        }
        jdbc.sql("DELETE FROM staff_service WHERE business_id = :businessId AND staff_id = :id")
                .param("businessId", businessId).param("id", staffId).update();
        for (UUID locationId : locationIds) {
            jdbc.sql("""
                            INSERT INTO staff_location (business_id, staff_id, location_id)
                            SELECT :businessId, :staffId, id FROM location WHERE business_id = :businessId AND id = :locationId
                            ON CONFLICT DO NOTHING""")
                    .param("businessId", businessId).param("staffId", staffId).param("locationId", locationId)
                    .update();
        }
        for (UUID serviceId : input.serviceIds().stream().distinct().toList()) {
            jdbc.sql("""
                            INSERT INTO staff_service (business_id, staff_id, service_id)
                            SELECT :businessId, :staffId, id FROM service WHERE business_id = :businessId AND id = :serviceId""")
                    .param("businessId", businessId).param("staffId", staffId).param("serviceId", serviceId)
                    .update();
        }
    }

    private Map<UUID, List<UUID>> links(String table, String column, UUID businessId) {
        record Link(UUID staffId, UUID targetId) {}
        return jdbc.sql("SELECT staff_id, " + column + " FROM " + table + " WHERE business_id = :businessId")
                .param("businessId", businessId)
                .query((rs, row) -> new Link(rs.getObject("staff_id", UUID.class), rs.getObject(column, UUID.class)))
                .list().stream()
                .collect(Collectors.groupingBy(Link::staffId, Collectors.mapping(Link::targetId, Collectors.toList())));
    }

    /** Active sign-ins linked to profiles, then open invites for profiles without one. */
    private Map<UUID, LoginView> logins(UUID businessId) {
        record Login(UUID staffId, LoginView view) {}
        List<Login> active = jdbc.sql("""
                        SELECT m.staff_id, u.email, m.role FROM membership m JOIN app_user u ON u.id = m.user_id
                        WHERE m.business_id = :businessId AND m.staff_id IS NOT NULL AND m.active""")
                .param("businessId", businessId)
                .query((rs, row) -> new Login(rs.getObject("staff_id", UUID.class),
                        new LoginView(rs.getString("email"), StaffRole.fromDb(rs.getString("role")), LoginStatus.ACTIVE)))
                .list();
        List<Login> invited = jdbc.sql("""
                        SELECT staff_id, email, role FROM invite
                        WHERE business_id = :businessId AND staff_id IS NOT NULL AND accepted_at IS NULL
                          AND revoked_at IS NULL AND expires_at > now()""")
                .param("businessId", businessId)
                .query((rs, row) -> new Login(rs.getObject("staff_id", UUID.class),
                        new LoginView(rs.getString("email"), StaffRole.fromDb(rs.getString("role")), LoginStatus.INVITED)))
                .list();
        Map<UUID, LoginView> result = new HashMap<>();
        invited.forEach(l -> result.put(l.staffId(), l.view()));
        active.forEach(l -> result.put(l.staffId(), l.view()));
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID business() {
        return TenantContext.requireBusinessId();
    }
}
