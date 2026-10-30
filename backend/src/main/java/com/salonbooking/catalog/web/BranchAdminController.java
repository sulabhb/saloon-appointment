package com.salonbooking.catalog.web;

import com.salonbooking.catalog.app.BranchAdminService;
import com.salonbooking.catalog.app.BranchAdminService.BranchDetail;
import com.salonbooking.catalog.app.BranchAdminService.BranchInput;
import com.salonbooking.catalog.app.BranchAdminService.BranchSummary;
import com.salonbooking.catalog.app.BranchAdminService.HoursInterval;
import com.salonbooking.identity.app.StaffPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Branch setup (owners; managers for hours, closures and services of their branches). */
@RestController
@RequestMapping("/api/v1/admin/locations")
class BranchAdminController {

    record BranchRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,48}[a-z0-9]$") String slug,
            @NotBlank @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @NotBlank @Size(max = 100) String suburb,
            @NotBlank @Size(max = 50) String state,
            @NotBlank @Size(max = 20) String postcode,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @NotBlank String timeZone,
            Boolean active) {

        BranchInput input() {
            return new BranchInput(name, slug, addressLine1, addressLine2, suburb, state, postcode, phone, email, timeZone);
        }
    }

    record HoursRequest(@NotNull List<@Valid Interval> hours) {
        record Interval(@Min(1) @Max(7) int weekday, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
    }

    record ClosureRequest(@NotNull LocalDate startsOn, @NotNull LocalDate endsOn, @Size(max = 200) String reason) {}

    record PolicyRequest(Integer slotIntervalMin, Integer minNoticeMin, Integer horizonDays) {}

    record OfferingRequest(boolean offered, @PositiveOrZero Long priceCents, @Min(5) @Max(600) Integer durationMin) {}

    private final BranchAdminService branches;

    BranchAdminController(BranchAdminService branches) {
        this.branches = branches;
    }

    @GetMapping
    List<BranchSummary> list(@AuthenticationPrincipal StaffPrincipal staff) {
        return branches.list(staff);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> create(@AuthenticationPrincipal StaffPrincipal staff, @Valid @RequestBody BranchRequest request) {
        return Map.of("id", branches.create(staff, request.input()));
    }

    @GetMapping("/{locationId}")
    BranchDetail get(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID locationId) {
        return branches.get(staff, locationId);
    }

    @PutMapping("/{locationId}")
    BranchDetail update(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID locationId,
            @Valid @RequestBody BranchRequest request) {
        branches.update(staff, locationId, request.input(), request.active() == null || request.active());
        return branches.get(staff, locationId);
    }

    @PutMapping("/{locationId}/policy")
    BranchDetail setPolicy(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID locationId,
            @RequestBody PolicyRequest request) {
        branches.setPolicy(staff, locationId, new BranchAdminService.BranchPolicy(request.slotIntervalMin(),
                request.minNoticeMin(), request.horizonDays()));
        return branches.get(staff, locationId);
    }

    @PutMapping("/{locationId}/hours")
    BranchDetail replaceHours(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID locationId,
            @Valid @RequestBody HoursRequest request) {
        branches.replaceHours(staff, locationId, request.hours().stream()
                .map(h -> new HoursInterval(h.weekday(), h.opensAt(), h.closesAt()))
                .toList());
        return branches.get(staff, locationId);
    }

    @PostMapping("/{locationId}/closures")
    @ResponseStatus(HttpStatus.CREATED)
    BranchDetail addClosure(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID locationId,
            @Valid @RequestBody ClosureRequest request) {
        branches.addClosure(staff, locationId, request.startsOn(), request.endsOn(), request.reason());
        return branches.get(staff, locationId);
    }

    @DeleteMapping("/{locationId}/closures/{closureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeClosure(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID locationId,
            @PathVariable UUID closureId) {
        branches.removeClosure(staff, locationId, closureId);
    }

    @PutMapping("/{locationId}/services/{serviceId}")
    BranchDetail setOffering(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID locationId,
            @PathVariable UUID serviceId, @Valid @RequestBody OfferingRequest request) {
        branches.setServiceOffering(staff, locationId, serviceId, request.offered(), request.priceCents(),
                request.durationMin());
        return branches.get(staff, locationId);
    }
}
