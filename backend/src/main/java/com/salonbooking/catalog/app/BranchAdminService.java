package com.salonbooking.catalog.app;

import com.salonbooking.identity.app.AdminAccess;
import static com.salonbooking.platform.app.BusinessSetupService.SLOT_INTERVALS;

import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Branch setup for owners and managers (LOC-1..5, SVC-3). Every query names the business
 * explicitly; row-level security checks it again.
 */
@Service
@Transactional
public class BranchAdminService {

    public record BranchInput(String name, String slug, String addressLine1, String addressLine2, String suburb,
            String state, String postcode, String phone, String email, String timeZone) {}

    public record BranchSummary(UUID id, String name, String slug, String suburb, String state, String timeZone,
            boolean active, int openDays, int servicesOffered, int staffCount) {}

    public record HoursInterval(int weekday, LocalTime opensAt, LocalTime closesAt) {}

    public record Closure(UUID id, LocalDate startsOn, LocalDate endsOn, String reason) {}

    /** A catalogue service and whether this branch offers it; overrides are null when the default applies. */
    public record BranchService(UUID serviceId, String name, String categoryName, boolean offered,
            Long priceCents, Integer durationMin, long defaultPriceCents, int defaultDurationMin) {}

    /** Branch overrides of the business's booking rules; null means "use the business setting". */
    public record BranchPolicy(Integer slotIntervalMin, Integer minNoticeMin, Integer horizonDays) {}

    public record BranchDetail(UUID id, String name, String slug, String addressLine1, String addressLine2,
            String suburb, String state, String postcode, String phone, String email, String timeZone, boolean active,
            List<HoursInterval> hours, List<Closure> closures, List<BranchService> services, BranchPolicy policy) {}

    private final JdbcClient jdbc;

    BranchAdminService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<BranchSummary> list(StaffPrincipal staff) {
        return jdbc.sql("""
                        SELECT l.id, l.name, l.slug, l.suburb, l.state, l.time_zone, l.active,
                               (SELECT count(DISTINCT h.weekday) FROM location_hours h WHERE h.location_id = l.id) AS open_days,
                               (SELECT count(*) FROM location_service ls JOIN service s ON s.id = ls.service_id
                                 WHERE ls.location_id = l.id AND s.active) AS services_offered,
                               (SELECT count(*) FROM staff_location sl JOIN staff st ON st.id = sl.staff_id
                                 WHERE sl.location_id = l.id AND st.active) AS staff_count
                        FROM location l
                        WHERE l.business_id = :businessId
                        ORDER BY l.active DESC, l.name""")
                .param("businessId", business())
                .query((rs, row) -> new BranchSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("slug"), rs.getString("suburb"), rs.getString("state"), rs.getString("time_zone"),
                        rs.getBoolean("active"), rs.getInt("open_days"), rs.getInt("services_offered"),
                        rs.getInt("staff_count")))
                .list().stream()
                .filter(b -> staff.canAccessLocation(b.id()))
                .toList();
    }

    public UUID create(StaffPrincipal staff, BranchInput input) {
        AdminAccess.requireOwner(staff);
        validate(input);
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("""
                            INSERT INTO location (id, business_id, name, slug, address_line1, address_line2, suburb, state,
                                                  postcode, phone, email, time_zone)
                            VALUES (:id, :businessId, :name, :slug, :address1, :address2, :suburb, :state, :postcode,
                                    :phone, :email, :timeZone)""")
                    .paramSource(params(input).addValue("id", id).addValue("businessId", business()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw slugTaken();
        }
        return id;
    }

    @Transactional(readOnly = true)
    public BranchDetail get(StaffPrincipal staff, UUID locationId) {
        if (!staff.canAccessLocation(locationId)) {
            throw new NotFoundException("Branch not found.");
        }
        UUID businessId = business();
        BranchDetail base = jdbc.sql("SELECT * FROM location WHERE business_id = :businessId AND id = :id")
                .param("businessId", businessId)
                .param("id", locationId)
                .query((rs, row) -> new BranchDetail(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("slug"), rs.getString("address_line1"), rs.getString("address_line2"),
                        rs.getString("suburb"), rs.getString("state"), rs.getString("postcode"), rs.getString("phone"),
                        rs.getString("email"), rs.getString("time_zone"), rs.getBoolean("active"),
                        List.of(), List.of(), List.of(), new BranchPolicy(rs.getObject("slot_interval_min", Integer.class),
                                rs.getObject("min_notice_min", Integer.class), rs.getObject("horizon_days", Integer.class))))
                .optional()
                .orElseThrow(() -> new NotFoundException("Branch not found."));

        List<HoursInterval> hours = jdbc.sql("""
                        SELECT weekday, opens_at, closes_at FROM location_hours
                        WHERE business_id = :businessId AND location_id = :id ORDER BY weekday, opens_at""")
                .param("businessId", businessId).param("id", locationId)
                .query((rs, row) -> new HoursInterval(rs.getInt("weekday"), rs.getObject("opens_at", LocalTime.class),
                        rs.getObject("closes_at", LocalTime.class)))
                .list();
        List<Closure> closures = jdbc.sql("""
                        SELECT id, starts_on, ends_on, reason FROM location_closure
                        WHERE business_id = :businessId AND location_id = :id AND ends_on >= current_date
                        ORDER BY starts_on""")
                .param("businessId", businessId).param("id", locationId)
                .query((rs, row) -> new Closure(rs.getObject("id", UUID.class), rs.getObject("starts_on", LocalDate.class),
                        rs.getObject("ends_on", LocalDate.class), rs.getString("reason")))
                .list();
        List<BranchService> services = jdbc.sql("""
                        SELECT s.id, s.name, c.name AS category_name, s.default_price, s.default_duration_min,
                               ls.service_id IS NOT NULL AS offered, ls.price, ls.duration_min
                        FROM service s
                        JOIN service_category c ON c.id = s.category_id
                        LEFT JOIN location_service ls ON ls.service_id = s.id AND ls.location_id = :id
                        WHERE s.business_id = :businessId AND s.active
                        ORDER BY c.sort_order, c.name, s.sort_order, s.name""")
                .param("businessId", businessId).param("id", locationId)
                .query((rs, row) -> new BranchService(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("category_name"), rs.getBoolean("offered"), rs.getObject("price", Long.class),
                        rs.getObject("duration_min", Integer.class), rs.getLong("default_price"),
                        rs.getInt("default_duration_min")))
                .list();

        return new BranchDetail(base.id(), base.name(), base.slug(), base.addressLine1(), base.addressLine2(),
                base.suburb(), base.state(), base.postcode(), base.phone(), base.email(), base.timeZone(),
                base.active(), hours, closures, services, base.policy());
    }

    public void update(StaffPrincipal staff, UUID locationId, BranchInput input, boolean active) {
        AdminAccess.requireOwner(staff);
        validate(input);
        int updated;
        try {
            updated = jdbc.sql("""
                            UPDATE location SET name = :name, slug = :slug, address_line1 = :address1,
                                   address_line2 = :address2, suburb = :suburb, state = :state, postcode = :postcode,
                                   phone = :phone, email = :email, time_zone = :timeZone, active = :active,
                                   updated_at = now()
                            WHERE business_id = :businessId AND id = :id""")
                    .paramSource(params(input)
                            .addValue("businessId", business())
                            .addValue("id", locationId)
                            .addValue("active", active))
                    .update();
        } catch (DuplicateKeyException e) {
            throw slugTaken();
        }
        if (updated == 0) {
            throw new NotFoundException("Branch not found.");
        }
    }

    /** Owners can give a branch its own slot interval, notice or horizon (DSH-17). */
    public void setPolicy(StaffPrincipal staff, UUID locationId, BranchPolicy policy) {
        AdminAccess.requireOwner(staff);
        if (policy.slotIntervalMin() != null && !SLOT_INTERVALS.contains(policy.slotIntervalMin())) {
            throw invalid("Start times can be every 5, 10, 15, 20, 30 or 60 minutes.");
        }
        if (policy.minNoticeMin() != null && (policy.minNoticeMin() < 0 || policy.minNoticeMin() > 14 * 24 * 60)) {
            throw invalid("Minimum notice must be between none and 14 days.");
        }
        if (policy.horizonDays() != null && (policy.horizonDays() < 1 || policy.horizonDays() > 365)) {
            throw invalid("Customers can book between 1 and 365 days ahead.");
        }
        int updated = jdbc.sql("""
                        UPDATE location SET slot_interval_min = :slot, min_notice_min = :notice, horizon_days = :horizon,
                               updated_at = now()
                        WHERE business_id = :businessId AND id = :id""")
                .param("slot", policy.slotIntervalMin()).param("notice", policy.minNoticeMin())
                .param("horizon", policy.horizonDays()).param("businessId", business()).param("id", locationId)
                .update();
        if (updated == 0) {
            throw new NotFoundException("Branch not found.");
        }
    }

    /** Replaces the weekly opening hours. Several intervals per day are allowed if they do not overlap. */
    public void replaceHours(StaffPrincipal staff, UUID locationId, List<HoursInterval> hours) {
        AdminAccess.requireBranch(staff, locationId);
        requireBranchExists(locationId);
        validateHours(hours);
        UUID businessId = business();
        jdbc.sql("DELETE FROM location_hours WHERE business_id = :businessId AND location_id = :id")
                .param("businessId", businessId).param("id", locationId).update();
        for (HoursInterval h : hours) {
            jdbc.sql("""
                            INSERT INTO location_hours (id, business_id, location_id, weekday, opens_at, closes_at)
                            VALUES (:id, :businessId, :locationId, :weekday, :opensAt, :closesAt)""")
                    .param("id", UUID.randomUUID())
                    .param("businessId", businessId)
                    .param("locationId", locationId)
                    .param("weekday", h.weekday())
                    .param("opensAt", h.opensAt())
                    .param("closesAt", h.closesAt())
                    .update();
        }
    }

    public UUID addClosure(StaffPrincipal staff, UUID locationId, LocalDate startsOn, LocalDate endsOn, String reason) {
        AdminAccess.requireBranch(staff, locationId);
        requireBranchExists(locationId);
        if (endsOn.isBefore(startsOn)) {
            throw invalid("The last closed day must be on or after the first.");
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO location_closure (id, business_id, location_id, starts_on, ends_on, reason)
                        VALUES (:id, :businessId, :locationId, :startsOn, :endsOn, :reason)""")
                .param("id", id)
                .param("businessId", business())
                .param("locationId", locationId)
                .param("startsOn", startsOn)
                .param("endsOn", endsOn)
                .param("reason", blankToNull(reason))
                .update();
        return id;
    }

    public void removeClosure(StaffPrincipal staff, UUID locationId, UUID closureId) {
        AdminAccess.requireBranch(staff, locationId);
        int removed = jdbc.sql("DELETE FROM location_closure WHERE business_id = :businessId AND location_id = :locationId AND id = :id")
                .param("businessId", business()).param("locationId", locationId).param("id", closureId)
                .update();
        if (removed == 0) {
            throw new NotFoundException("Closure not found.");
        }
    }

    /** Offers or withdraws a service at a branch, with optional branch price and duration (SVC-3). */
    public void setServiceOffering(StaffPrincipal staff, UUID locationId, UUID serviceId, boolean offered,
            Long priceCents, Integer durationMin) {
        AdminAccess.requireBranch(staff, locationId);
        requireBranchExists(locationId);
        UUID businessId = business();
        if (!offered) {
            jdbc.sql("DELETE FROM location_service WHERE business_id = :businessId AND location_id = :locationId AND service_id = :serviceId")
                    .param("businessId", businessId).param("locationId", locationId).param("serviceId", serviceId)
                    .update();
            return;
        }
        boolean serviceExists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM service WHERE business_id = :businessId AND id = :id)")
                .param("businessId", businessId).param("id", serviceId).query(Boolean.class).single();
        if (!serviceExists) {
            throw new NotFoundException("Service not found.");
        }
        jdbc.sql("""
                        INSERT INTO location_service (business_id, location_id, service_id, price, duration_min)
                        VALUES (:businessId, :locationId, :serviceId, :price, :duration)
                        ON CONFLICT (location_id, service_id)
                        DO UPDATE SET price = EXCLUDED.price, duration_min = EXCLUDED.duration_min, updated_at = now()""")
                .param("businessId", businessId)
                .param("locationId", locationId)
                .param("serviceId", serviceId)
                .param("price", priceCents)
                .param("duration", durationMin)
                .update();
    }

    static void validateHours(List<HoursInterval> hours) {
        for (HoursInterval h : hours) {
            if (h.weekday() < 1 || h.weekday() > 7) {
                throw invalid("Weekdays are numbered 1 (Monday) to 7 (Sunday).");
            }
            if (!h.opensAt().isBefore(h.closesAt())) {
                throw invalid("Each opening time must be before its closing time.");
            }
        }
        List<HoursInterval> sorted = hours.stream()
                .sorted(Comparator.comparingInt(HoursInterval::weekday).thenComparing(HoursInterval::opensAt))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            HoursInterval previous = sorted.get(i - 1);
            HoursInterval current = sorted.get(i);
            if (previous.weekday() == current.weekday() && current.opensAt().isBefore(previous.closesAt())) {
                throw invalid("Opening hours on the same day must not overlap.");
            }
        }
    }

    private void requireBranchExists(UUID locationId) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM location WHERE business_id = :businessId AND id = :id)")
                .param("businessId", business()).param("id", locationId).query(Boolean.class).single();
        if (!exists) {
            throw new NotFoundException("Branch not found.");
        }
    }

    private static void validate(BranchInput input) {
        if (!ZoneId.getAvailableZoneIds().contains(input.timeZone())) {
            throw invalid("Unknown time zone.");
        }
    }

    /** All values in one source: JdbcClient's paramSource replaces parameters set with param(). */
    private static MapSqlParameterSource params(BranchInput input) {
        return new MapSqlParameterSource()
                .addValue("name", input.name().trim())
                .addValue("slug", input.slug().trim().toLowerCase(Locale.ROOT))
                .addValue("address1", input.addressLine1().trim())
                .addValue("address2", blankToNull(input.addressLine2()))
                .addValue("suburb", input.suburb().trim())
                .addValue("state", input.state().trim())
                .addValue("postcode", input.postcode().trim())
                .addValue("phone", blankToNull(input.phone()))
                .addValue("email", blankToNull(input.email()))
                .addValue("timeZone", input.timeZone());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID business() {
        return TenantContext.requireBusinessId();
    }

    private static ApiException slugTaken() {
        return new ApiException(HttpStatus.CONFLICT, "SLUG_TAKEN", "Another branch already uses that link name.");
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
