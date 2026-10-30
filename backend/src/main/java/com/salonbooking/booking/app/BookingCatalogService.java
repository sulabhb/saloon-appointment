package com.salonbooking.booking.app;

import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The menu and stylists customers choose from at one branch (BKG-3, BKG-5). */
@Service
@Transactional(readOnly = true)
public class BookingCatalogService {

    public record ServiceOption(UUID id, String name, String description, int durationMin, long priceCents,
            boolean priceIsFrom, String customerNotice) {}

    public record CategoryOption(String name, List<ServiceOption> services) {}

    /** Only bookable-online stylists; {@code serviceIds} lets the page show who does what (AVL-3). */
    public record StylistOption(UUID id, String displayName, String bio, List<UUID> serviceIds) {}

    public record BranchCatalog(List<CategoryOption> categories, List<StylistOption> stylists) {}

    private final JdbcClient jdbc;

    BookingCatalogService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public BranchCatalog catalog(String locationSlug) {
        UUID businessId = TenantContext.requireBusinessId();
        UUID locationId = jdbc.sql("SELECT id FROM location WHERE business_id = :businessId AND slug = :slug AND active")
                .param("businessId", businessId).param("slug", locationSlug)
                .query(UUID.class).optional()
                .orElseThrow(() -> new NotFoundException("Branch not found."));
        return catalog(businessId, locationId, false);
    }

    /** For staff booking: stylists who are not bookable online are included (STF-8). */
    public BranchCatalog staffCatalog(UUID locationId) {
        UUID businessId = TenantContext.requireBusinessId();
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM location WHERE business_id = :businessId AND id = :id AND active)")
                .param("businessId", businessId).param("id", locationId).query(Boolean.class).single();
        if (!exists) {
            throw new NotFoundException("Branch not found.");
        }
        return catalog(businessId, locationId, true);
    }

    private BranchCatalog catalog(UUID businessId, UUID locationId, boolean includeOffline) {
        record Row(String category, ServiceOption service) {}
        Map<String, List<ServiceOption>> byCategory = jdbc.sql("""
                        SELECT c.name AS category, s.id, s.name, s.description,
                               coalesce(ls.duration_min, s.default_duration_min) AS duration_min,
                               coalesce(ls.price, s.default_price) AS price, s.price_is_from, s.customer_notice
                        FROM service s
                        JOIN service_category c ON c.id = s.category_id
                        JOIN location_service ls ON ls.service_id = s.id AND ls.location_id = :locationId
                        WHERE s.business_id = :businessId AND s.active
                        ORDER BY c.sort_order, c.name, s.sort_order, s.name""")
                .param("businessId", businessId).param("locationId", locationId)
                .query((rs, row) -> new Row(rs.getString("category"), new ServiceOption(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("description"), rs.getInt("duration_min"), rs.getLong("price"),
                        rs.getBoolean("price_is_from"), rs.getString("customer_notice"))))
                .list().stream()
                .collect(Collectors.groupingBy(Row::category, LinkedHashMap::new,
                        Collectors.mapping(Row::service, Collectors.toList())));

        Map<UUID, List<UUID>> skills = jdbc.sql("SELECT staff_id, service_id FROM staff_service WHERE business_id = :businessId")
                .param("businessId", businessId)
                .query((rs, row) -> Map.entry(rs.getObject("staff_id", UUID.class), rs.getObject("service_id", UUID.class)))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        List<StylistOption> stylists = jdbc.sql("""
                        SELECT st.id, st.display_name, st.bio FROM staff st
                        JOIN staff_location sl ON sl.staff_id = st.id AND sl.location_id = :locationId
                        WHERE st.business_id = :businessId AND st.active AND (st.bookable_online OR :includeOffline)
                        ORDER BY st.display_name""")
                .param("businessId", businessId).param("locationId", locationId).param("includeOffline", includeOffline)
                .query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new StylistOption(id, rs.getString("display_name"), rs.getString("bio"),
                            skills.getOrDefault(id, List.of()));
                })
                .list();

        return new BranchCatalog(byCategory.entrySet().stream()
                .map(e -> new CategoryOption(e.getKey(), e.getValue())).toList(), stylists);
    }
}
