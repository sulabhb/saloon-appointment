package com.salonbooking.catalog.app;

import com.salonbooking.identity.app.AdminAccess;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The business's service menu (SVC-1, SVC-2, SVC-5..7). Owners edit it; managers can read it. */
@Service
@Transactional
public class CatalogAdminService {

    public record ServiceInput(UUID categoryId, String name, String description, int durationMin,
            int bufferAfterMin, long priceCents, boolean priceIsFrom, String customerNotice) {}

    public record ServiceView(UUID id, UUID categoryId, String name, String description, int durationMin,
            int bufferAfterMin, long priceCents, boolean priceIsFrom, String customerNotice, boolean active,
            int branchCount, int staffCount) {}

    public record CategoryView(UUID id, String name, int sortOrder, List<ServiceView> services) {}

    private final JdbcClient jdbc;

    CatalogAdminService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Categories in menu order, each with its services (inactive ones last). */
    @Transactional(readOnly = true)
    public List<CategoryView> catalog() {
        UUID businessId = business();
        Map<UUID, List<ServiceView>> servicesByCategory = jdbc.sql("""
                        SELECT s.*,
                               (SELECT count(*) FROM location_service ls WHERE ls.service_id = s.id) AS branch_count,
                               (SELECT count(*) FROM staff_service ss JOIN staff st ON st.id = ss.staff_id
                                 WHERE ss.service_id = s.id AND st.active) AS staff_count
                        FROM service s
                        WHERE s.business_id = :businessId
                        ORDER BY s.active DESC, s.sort_order, s.name""")
                .param("businessId", businessId)
                .query((rs, row) -> new ServiceView(rs.getObject("id", UUID.class), rs.getObject("category_id", UUID.class),
                        rs.getString("name"), rs.getString("description"), rs.getInt("default_duration_min"),
                        rs.getInt("buffer_after_min"), rs.getLong("default_price"), rs.getBoolean("price_is_from"),
                        rs.getString("customer_notice"), rs.getBoolean("active"), rs.getInt("branch_count"),
                        rs.getInt("staff_count")))
                .list().stream()
                .collect(Collectors.groupingBy(ServiceView::categoryId));

        return jdbc.sql("SELECT id, name, sort_order FROM service_category WHERE business_id = :businessId ORDER BY sort_order, name")
                .param("businessId", businessId)
                .query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new CategoryView(id, rs.getString("name"), rs.getInt("sort_order"),
                            servicesByCategory.getOrDefault(id, List.of()));
                })
                .list();
    }

    public UUID createCategory(StaffPrincipal staff, String name) {
        AdminAccess.requireOwner(staff);
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO service_category (id, business_id, name, sort_order)
                        VALUES (:id, :businessId, :name,
                                (SELECT coalesce(max(sort_order), 0) + 1 FROM service_category WHERE business_id = :businessId))""")
                .param("id", id).param("businessId", business()).param("name", name.trim())
                .update();
        return id;
    }

    public void renameCategory(StaffPrincipal staff, UUID categoryId, String name) {
        AdminAccess.requireOwner(staff);
        int updated = jdbc.sql("UPDATE service_category SET name = :name, updated_at = now() WHERE business_id = :businessId AND id = :id")
                .param("name", name.trim()).param("businessId", business()).param("id", categoryId)
                .update();
        if (updated == 0) {
            throw new NotFoundException("Category not found.");
        }
    }

    /** Creates a service and offers it at the given branches straight away. */
    public UUID createService(StaffPrincipal staff, ServiceInput input, List<UUID> offerAtLocationIds) {
        AdminAccess.requireOwner(staff);
        requireCategory(input.categoryId());
        UUID id = UUID.randomUUID();
        UUID businessId = business();
        jdbc.sql("""
                        INSERT INTO service (id, business_id, category_id, name, description, default_duration_min,
                                             buffer_after_min, default_price, price_is_from, customer_notice, sort_order)
                        VALUES (:id, :businessId, :categoryId, :name, :description, :duration, :buffer, :price,
                                :priceIsFrom, :notice,
                                (SELECT coalesce(max(sort_order), 0) + 1 FROM service WHERE business_id = :businessId))""")
                .paramSource(params(input).addValue("id", id).addValue("businessId", businessId))
                .update();
        for (UUID locationId : offerAtLocationIds) {
            jdbc.sql("""
                            INSERT INTO location_service (business_id, location_id, service_id)
                            SELECT :businessId, l.id, :serviceId FROM location l
                            WHERE l.business_id = :businessId AND l.id = :locationId""")
                    .param("businessId", businessId).param("serviceId", id).param("locationId", locationId)
                    .update();
        }
        return id;
    }

    public void updateService(StaffPrincipal staff, UUID serviceId, ServiceInput input, boolean active) {
        AdminAccess.requireOwner(staff);
        requireCategory(input.categoryId());
        int updated = jdbc.sql("""
                        UPDATE service SET category_id = :categoryId, name = :name, description = :description,
                               default_duration_min = :duration, buffer_after_min = :buffer, default_price = :price,
                               price_is_from = :priceIsFrom, customer_notice = :notice, active = :active,
                               updated_at = now()
                        WHERE business_id = :businessId AND id = :id""")
                .paramSource(params(input).addValue("businessId", business()).addValue("id", serviceId)
                        .addValue("active", active))
                .update();
        if (updated == 0) {
            throw new NotFoundException("Service not found.");
        }
    }

    private void requireCategory(UUID categoryId) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM service_category WHERE business_id = :businessId AND id = :id)")
                .param("businessId", business()).param("id", categoryId).query(Boolean.class).single();
        if (!exists) {
            throw new NotFoundException("Category not found.");
        }
    }

    /** All values in one source: JdbcClient's paramSource replaces parameters set with param(). */
    private static MapSqlParameterSource params(ServiceInput input) {
        return new MapSqlParameterSource()
                .addValue("categoryId", input.categoryId())
                .addValue("name", input.name().trim())
                .addValue("description", blankToNull(input.description()))
                .addValue("duration", input.durationMin())
                .addValue("buffer", input.bufferAfterMin())
                .addValue("price", input.priceCents())
                .addValue("priceIsFrom", input.priceIsFrom())
                .addValue("notice", blankToNull(input.customerNotice()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID business() {
        return TenantContext.requireBusinessId();
    }
}
