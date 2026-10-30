package com.salonbooking.platform.web;

import com.salonbooking.identity.app.InviteService.IssuedInvite;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.platform.app.PlatformBusinessService;
import com.salonbooking.platform.app.PlatformBusinessService.BusinessDetail;
import com.salonbooking.platform.app.PlatformBusinessService.CreateBusiness;
import com.salonbooking.platform.app.PlatformBusinessService.CreatedBusiness;
import com.salonbooking.platform.data.PlatformBusinessQueries.BusinessSummary;
import com.salonbooking.platform.domain.BusinessStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

/** Platform console API. Platform admins only (see SecurityConfiguration). */
@RestController
@RequestMapping("/api/v1/platform/businesses")
class PlatformBusinessController {

    record CreateBusinessRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$") String slug,
            @NotBlank @Size(max = 200) String legalName,
            @NotBlank @Size(max = 200) String displayName,
            @NotBlank String defaultTimeZone,
            @NotBlank @Email @Size(max = 254) String ownerEmail,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String accentColor,
            @Size(max = 500) String description) {}

    record StatusRequest(@NotNull BusinessStatus status) {}

    record OwnerInviteRequest(@NotBlank @Email @Size(max = 254) String email) {}

    private final PlatformBusinessService service;

    PlatformBusinessController(PlatformBusinessService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedBusiness create(@AuthenticationPrincipal StaffPrincipal admin, @Valid @RequestBody CreateBusinessRequest r) {
        return service.create(new CreateBusiness(r.slug(), r.legalName(), r.displayName(), r.defaultTimeZone(),
                r.ownerEmail(), r.accentColor(), r.description()), admin.userId());
    }

    @GetMapping
    List<BusinessSummary> list() {
        return service.list();
    }

    @GetMapping("/{businessId}")
    BusinessDetail get(@PathVariable UUID businessId) {
        return service.get(businessId);
    }

    @PostMapping("/{businessId}/status")
    BusinessSummary changeStatus(@PathVariable UUID businessId, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(businessId, request.status());
    }

    @PostMapping("/{businessId}/owner-invites")
    @ResponseStatus(HttpStatus.CREATED)
    IssuedInvite inviteOwner(@AuthenticationPrincipal StaffPrincipal admin, @PathVariable UUID businessId,
            @Valid @RequestBody OwnerInviteRequest request) {
        return service.inviteOwner(businessId, request.email(), admin.userId());
    }

    @DeleteMapping("/{businessId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInvite(@PathVariable UUID businessId, @PathVariable UUID inviteId) {
        service.revokeInvite(businessId, inviteId);
    }
}
