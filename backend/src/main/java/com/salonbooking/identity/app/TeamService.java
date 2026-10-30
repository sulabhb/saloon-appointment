package com.salonbooking.identity.app;

import com.salonbooking.identity.app.InviteService.InviteView;
import com.salonbooking.identity.app.InviteService.IssuedInvite;
import com.salonbooking.identity.data.MembershipRepository;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who can sign in to the business, and invites for new people (STF-2, STF-9). Owners only. */
@Service
public class TeamService {

    public record MemberView(UUID id, UUID userId, String fullName, String email, StaffRole role, boolean active,
            UUID staffId, List<UUID> locationIds) {}

    private final JdbcClient jdbc;
    private final InviteService inviteService;
    private final MembershipRepository memberships;

    TeamService(JdbcClient jdbc, InviteService inviteService, MembershipRepository memberships) {
        this.jdbc = jdbc;
        this.inviteService = inviteService;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(StaffPrincipal staff) {
        AdminAccess.requireOwner(staff);
        UUID businessId = TenantContext.requireBusinessId();
        record Link(UUID membershipId, UUID locationId) {}
        Map<UUID, List<UUID>> locations = jdbc.sql(
                        "SELECT membership_id, location_id FROM membership_location WHERE business_id = :businessId")
                .param("businessId", businessId)
                .query((rs, row) -> new Link(rs.getObject("membership_id", UUID.class), rs.getObject("location_id", UUID.class)))
                .list().stream()
                .collect(Collectors.groupingBy(Link::membershipId, Collectors.mapping(Link::locationId, Collectors.toList())));
        return jdbc.sql("""
                        SELECT m.id, m.user_id, u.full_name, u.email, m.role, m.active, m.staff_id
                        FROM membership m JOIN app_user u ON u.id = m.user_id
                        WHERE m.business_id = :businessId
                        ORDER BY m.active DESC, u.full_name""")
                .param("businessId", businessId)
                .query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new MemberView(id, rs.getObject("user_id", UUID.class), rs.getString("full_name"),
                            rs.getString("email"), StaffRole.fromDb(rs.getString("role")), rs.getBoolean("active"),
                            rs.getObject("staff_id", UUID.class), locations.getOrDefault(id, List.of()));
                })
                .list();
    }

    /** Invites someone with a role, optionally linked to their staff profile and given branch access. */
    @Transactional
    public IssuedInvite invite(StaffPrincipal staff, String email, StaffRole role, UUID staffId, List<UUID> locationIds) {
        AdminAccess.requireOwner(staff);
        return inviteService.issue(email, role, staff.userId(), staffId, locationIds);
    }

    public List<InviteView> invites(StaffPrincipal staff) {
        AdminAccess.requireOwner(staff);
        return inviteService.list();
    }

    public void revokeInvite(StaffPrincipal staff, UUID inviteId) {
        AdminAccess.requireOwner(staff);
        inviteService.revoke(inviteId);
    }

    /** Removes someone's access. Their sessions stop working on the next request (STF-9). */
    @Transactional
    public void deactivate(StaffPrincipal staff, UUID membershipId) {
        AdminAccess.requireOwner(staff);
        var membership = memberships.findById(membershipId)
                .orElseThrow(() -> new NotFoundException("Team member not found."));
        if (membership.getUserId().equals(staff.userId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_REMOVE_SELF", "You cannot remove your own access.");
        }
        membership.deactivate();
    }
}
