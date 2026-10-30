package com.salonbooking.platform.app;

import com.salonbooking.identity.app.AdminAccess;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.platform.data.BusinessRepository;
import com.salonbooking.platform.domain.Business;
import com.salonbooking.platform.domain.BusinessStatus;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.tenancy.TenantContext;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The owner's view of their own business: profile and branding (TEN-7), the setup checklist,
 * and going live once it is complete (TEN-3, DSH-18).
 */
@Service
@Transactional
public class BusinessSetupService {

    public record Profile(UUID id, String slug, String displayName, String description, String accentColor,
            String contactEmail, BusinessStatus status) {}

    /** Each step must be true before an onboarding business can go live. */
    public record SetupStatus(boolean branchWithHours, boolean serviceOffered, boolean bookableStaff,
            BusinessStatus status, boolean canGoLive) {}

    /**
     * Online booking rules for the whole business (DSH-17); branches can override the first three.
     * Staff bookings ignore notice, horizon and the per-customer limit.
     */
    public record Policy(int slotIntervalMin, int minNoticeMin, int horizonDays, int cancelCutoffMin,
            int maxFutureBookingsPerContact) {}

    public record Overview(Profile profile, SetupStatus setup, Policy policy) {}

    public static final Set<Integer> SLOT_INTERVALS = Set.of(5, 10, 15, 20, 30, 60);

    private final JdbcClient jdbc;
    private final BusinessRepository businesses;
    private final Clock clock;

    BusinessSetupService(JdbcClient jdbc, BusinessRepository businesses, Clock clock) {
        this.jdbc = jdbc;
        this.businesses = businesses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        UUID businessId = TenantContext.requireBusinessId();
        Profile profile = jdbc.sql("""
                        SELECT id, slug, display_name, description, accent_color, contact_email, status
                        FROM business WHERE id = :id""")
                .param("id", businessId)
                .query((rs, row) -> new Profile(rs.getObject("id", UUID.class), rs.getString("slug"),
                        rs.getString("display_name"), rs.getString("description"), rs.getString("accent_color"),
                        rs.getString("contact_email"),
                        BusinessStatus.valueOf(rs.getString("status").toUpperCase(Locale.ROOT))))
                .single();
        Policy policy = jdbc.sql("""
                        SELECT slot_interval_min, min_notice_min, horizon_days, cancel_cutoff_min, max_future_bookings_per_contact
                        FROM business WHERE id = :id""")
                .param("id", businessId)
                .query((rs, row) -> new Policy(rs.getInt("slot_interval_min"), rs.getInt("min_notice_min"),
                        rs.getInt("horizon_days"), rs.getInt("cancel_cutoff_min"), rs.getInt("max_future_bookings_per_contact")))
                .single();
        return new Overview(profile, setupStatus(businessId, profile.status()), policy);
    }

    /** Owners set the online booking rules (requirements 4.1). */
    public Overview updatePolicy(StaffPrincipal staff, Policy policy) {
        AdminAccess.requireOwner(staff);
        validate(policy);
        jdbc.sql("""
                        UPDATE business SET slot_interval_min = :slot, min_notice_min = :notice, horizon_days = :horizon,
                               cancel_cutoff_min = :cutoff, max_future_bookings_per_contact = :max, updated_at = now()
                        WHERE id = :id""")
                .param("slot", policy.slotIntervalMin()).param("notice", policy.minNoticeMin())
                .param("horizon", policy.horizonDays()).param("cutoff", policy.cancelCutoffMin())
                .param("max", policy.maxFutureBookingsPerContact()).param("id", TenantContext.requireBusinessId())
                .update();
        return overview();
    }

    static void validate(Policy p) {
        if (!SLOT_INTERVALS.contains(p.slotIntervalMin())) {
            throw invalid("Start times can be every 5, 10, 15, 20, 30 or 60 minutes.");
        }
        if (p.minNoticeMin() < 0 || p.minNoticeMin() > 14 * 24 * 60) {
            throw invalid("Minimum notice must be between none and 14 days.");
        }
        if (p.horizonDays() < 1 || p.horizonDays() > 365) {
            throw invalid("Customers can book between 1 and 365 days ahead.");
        }
        if (p.cancelCutoffMin() < 0 || p.cancelCutoffMin() > 14 * 24 * 60) {
            throw invalid("The cancellation cut-off must be between none and 14 days.");
        }
        if (p.maxFutureBookingsPerContact() < 1 || p.maxFutureBookingsPerContact() > 50) {
            throw invalid("Allow between 1 and 50 upcoming bookings per customer.");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    public void updateProfile(StaffPrincipal staff, String displayName, String description, String accentColor,
            String contactEmail) {
        AdminAccess.requireOwner(staff);
        jdbc.sql("""
                        UPDATE business SET display_name = :name, description = :description, accent_color = :accent,
                               contact_email = :email, updated_at = now()
                        WHERE id = :id""")
                .param("name", displayName.trim())
                .param("description", blankToNull(description))
                .param("accent", blankToNull(accentColor))
                .param("email", blankToNull(contactEmail))
                .param("id", TenantContext.requireBusinessId())
                .update();
    }

    /** Owners can take their own business live once setup is complete. Suspending stays with the platform team. */
    public Overview goLive(StaffPrincipal staff) {
        AdminAccess.requireOwner(staff);
        UUID businessId = TenantContext.requireBusinessId();
        Business business = businesses.findById(businessId).orElseThrow();
        SetupStatus setup = setupStatus(businessId, business.getStatus());
        if (!setup.canGoLive()) {
            throw new ApiException(HttpStatus.CONFLICT, "SETUP_INCOMPLETE",
                    "Finish setup first: a branch with opening hours, a service it offers, and a bookable staff member with shifts there.");
        }
        business.changeStatus(BusinessStatus.LIVE, clock.instant());
        // overview() reads with plain SQL, which does not see unflushed Hibernate changes.
        businesses.saveAndFlush(business);
        return overview();
    }

    private SetupStatus setupStatus(UUID businessId, BusinessStatus status) {
        record Checks(boolean branch, boolean service, boolean staff) {}
        Checks checks = jdbc.sql("""
                        SELECT
                          EXISTS (SELECT 1 FROM location l WHERE l.business_id = :b AND l.active
                                    AND EXISTS (SELECT 1 FROM location_hours h WHERE h.location_id = l.id)) AS branch,
                          EXISTS (SELECT 1 FROM location_service ls
                                    JOIN location l ON l.id = ls.location_id
                                    JOIN service s ON s.id = ls.service_id
                                   WHERE ls.business_id = :b AND l.active AND s.active) AS service,
                          EXISTS (SELECT 1 FROM staff st
                                    JOIN staff_location sl ON sl.staff_id = st.id
                                    JOIN staff_service ss ON ss.staff_id = st.id
                                    JOIN location_service ls ON ls.location_id = sl.location_id AND ls.service_id = ss.service_id
                                    JOIN location l ON l.id = sl.location_id
                                    JOIN service s ON s.id = ss.service_id
                                    JOIN staff_shift sh ON sh.staff_id = st.id AND sh.location_id = sl.location_id
                                   WHERE st.business_id = :b AND st.active AND st.bookable_online AND l.active AND s.active) AS staff""")
                .param("b", businessId)
                .query((rs, row) -> new Checks(rs.getBoolean("branch"), rs.getBoolean("service"), rs.getBoolean("staff")))
                .single();
        boolean ready = checks.branch() && checks.service() && checks.staff();
        return new SetupStatus(checks.branch(), checks.service(), checks.staff(), status,
                ready && status == BusinessStatus.ONBOARDING);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
