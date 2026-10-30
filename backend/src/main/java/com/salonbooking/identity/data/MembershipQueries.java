package com.salonbooking.identity.data;

import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Membership lookups. The membership tables are business-owned, so row-level security limits
 * every query to the business bound in TenantContext (or all businesses in system scope).
 */
@Repository
public class MembershipQueries {

    /** A business the user can work in, for the business picker. */
    public record BusinessMembership(UUID businessId, String slug, String displayName, StaffRole role) {}

    /** What the user may do in the bound business. {@code locationIds} is null when they see every branch. */
    /** {@code staffId}: the person's own staff profile, if they perform services. */
    public record Access(UUID membershipId, StaffRole role, Set<UUID> locationIds, UUID staffId) {}

    private final JdbcClient jdbc;

    MembershipQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Active memberships in businesses that are not closed. Visible only in system scope. */
    public List<BusinessMembership> activeMemberships(UUID userId) {
        return jdbc.sql("""
                        SELECT m.business_id, b.slug, b.display_name, m.role
                        FROM membership m
                        JOIN business b ON b.id = m.business_id
                        WHERE m.user_id = :userId AND m.active AND b.status <> 'closed'
                        ORDER BY b.display_name""")
                .param("userId", userId)
                .query((rs, row) -> new BusinessMembership(rs.getObject("business_id", UUID.class),
                        rs.getString("slug"), rs.getString("display_name"), StaffRole.fromDb(rs.getString("role"))))
                .list();
    }

    /** The user's access in the bound business, if they have an active membership and it is not closed. */
    public Optional<Access> accessInCurrentBusiness(UUID userId) {
        Optional<Access> membership = jdbc.sql("""
                        SELECT m.id, m.role, m.staff_id
                        FROM membership m
                        JOIN business b ON b.id = m.business_id
                        WHERE m.business_id = :businessId AND m.user_id = :userId AND m.active
                          AND b.status <> 'closed'""")
                .param("businessId", TenantContext.requireBusinessId())
                .param("userId", userId)
                .query((rs, row) -> new Access(rs.getObject("id", UUID.class), StaffRole.fromDb(rs.getString("role")), null,
                        rs.getObject("staff_id", UUID.class)))
                .optional();
        return membership.map(m -> m.role().seesAllBranches() ? m : new Access(m.membershipId(), m.role(),
                Set.copyOf(jdbc.sql("SELECT location_id FROM membership_location WHERE membership_id = :id")
                        .param("id", m.membershipId())
                        .query(UUID.class)
                        .list()), m.staffId()));
    }
}
