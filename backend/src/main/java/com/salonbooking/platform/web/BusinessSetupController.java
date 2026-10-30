package com.salonbooking.platform.web;

import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.platform.app.BusinessSetupService;
import com.salonbooking.platform.app.BusinessSetupService.Policy;
import com.salonbooking.platform.app.BusinessSetupService.Overview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in owner's business: profile, setup checklist and going live. */
@RestController
@RequestMapping("/api/v1/admin/business")
class BusinessSetupController {

    record ProfileRequest(
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 500) String description,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String accentColor,
            @Email @Size(max = 254) String contactEmail) {}

    record PolicyRequest(int slotIntervalMin, int minNoticeMin, int horizonDays, int cancelCutoffMin,
            int maxFutureBookingsPerContact) {}

    private final BusinessSetupService setup;

    BusinessSetupController(BusinessSetupService setup) {
        this.setup = setup;
    }

    @GetMapping
    Overview overview() {
        return setup.overview();
    }

    @PutMapping
    Overview updateProfile(@AuthenticationPrincipal StaffPrincipal staff, @Valid @RequestBody ProfileRequest request) {
        setup.updateProfile(staff, request.displayName(), request.description(), request.accentColor(),
                request.contactEmail());
        return setup.overview();
    }

    @PutMapping("/policy")
    Overview updatePolicy(@AuthenticationPrincipal StaffPrincipal staff, @RequestBody PolicyRequest r) {
        return setup.updatePolicy(staff, new Policy(r.slotIntervalMin(), r.minNoticeMin(), r.horizonDays(),
                r.cancelCutoffMin(), r.maxFutureBookingsPerContact()));
    }

    @PostMapping("/go-live")
    Overview goLive(@AuthenticationPrincipal StaffPrincipal staff) {
        return setup.goLive(staff);
    }
}
