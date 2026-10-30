package com.salonbooking.staff.web;

import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.staff.app.StaffAdminService;
import com.salonbooking.staff.app.StaffAdminService.StaffInput;
import com.salonbooking.staff.app.StaffAdminService.StaffView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff profiles. Every change returns the full list so the console can redraw it. */
@RestController
@RequestMapping("/api/v1/admin/staff")
class StaffAdminController {

    record StaffRequest(
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 500) String bio,
            Boolean bookableOnline,
            Boolean active,
            List<UUID> locationIds,
            List<UUID> serviceIds) {

        StaffInput input() {
            return new StaffInput(displayName, bio, bookableOnline == null || bookableOnline,
                    locationIds == null ? List.of() : locationIds, serviceIds == null ? List.of() : serviceIds);
        }
    }

    private final StaffAdminService staffAdmin;

    StaffAdminController(StaffAdminService staffAdmin) {
        this.staffAdmin = staffAdmin;
    }

    @GetMapping
    List<StaffView> list() {
        return staffAdmin.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    List<StaffView> create(@AuthenticationPrincipal StaffPrincipal staff, @Valid @RequestBody StaffRequest request) {
        staffAdmin.create(staff, request.input());
        return staffAdmin.list();
    }

    @PutMapping("/{staffId}")
    List<StaffView> update(@AuthenticationPrincipal StaffPrincipal staff, @PathVariable UUID staffId,
            @Valid @RequestBody StaffRequest request) {
        staffAdmin.update(staff, staffId, request.input(), request.active() == null || request.active());
        return staffAdmin.list();
    }
}
