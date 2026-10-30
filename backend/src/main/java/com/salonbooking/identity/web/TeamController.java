package com.salonbooking.identity.web;

import com.salonbooking.identity.app.InviteService.InviteView;
import com.salonbooking.identity.app.InviteService.IssuedInvite;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.identity.app.TeamService;
import com.salonbooking.identity.app.TeamService.MemberView;
import com.salonbooking.identity.domain.StaffRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Who can sign in to the business, and invites (owners only). */
@RestController
@RequestMapping("/api/v1/admin/team")
class TeamController {

    record InviteRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull StaffRole role,
            UUID staffId,
            List<UUID> locationIds) {}

    private final TeamService team;

    TeamController(TeamService team) {
        this.team = team;
    }

    @GetMapping("/members")
    List<MemberView> members(@AuthenticationPrincipal StaffPrincipal staff) {
        return team.members(staff);
    }

    @PostMapping("/members/{membershipId}/deactivate")
    List<MemberView> deactivate(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID membershipId) {
        team.deactivate(staff, membershipId);
        return team.members(staff);
    }

    @GetMapping("/invites")
    List<InviteView> invites(@AuthenticationPrincipal StaffPrincipal staff) {
        return team.invites(staff);
    }

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    IssuedInvite invite(@AuthenticationPrincipal StaffPrincipal staff, @Valid @RequestBody InviteRequest request) {
        return team.invite(staff, request.email(), request.role(), request.staffId(),
                request.locationIds() == null ? List.of() : request.locationIds());
    }

    @DeleteMapping("/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID inviteId) {
        team.revokeInvite(staff, inviteId);
    }
}
