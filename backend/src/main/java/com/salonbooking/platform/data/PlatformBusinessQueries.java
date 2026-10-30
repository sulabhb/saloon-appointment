package com.salonbooking.platform.data;

import com.salonbooking.platform.domain.BusinessStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for the platform console. */
@Repository
public class PlatformBusinessQueries {

    public record NewBusiness(UUID id, String slug, String legalName, String displayName, String defaultTimeZone,
            String accentColor, String description, String contactEmail) {}

    public record BusinessSummary(UUID id, String slug, String displayName, BusinessStatus status, Instant createdAt,
            int activeBranches, int activeStaff, int pendingInvites) {}

    private static final String SUMMARY_SQL = """
            SELECT b.id, b.slug, b.display_name, b.status, b.created_at,
                   (SELECT count(*) FROM location l WHERE l.business_id = b.id AND l.active) AS active_branches,
                   (SELECT count(*) FROM membership m WHERE m.business_id = b.id AND m.active) AS active_staff,
                   (SELECT count(*) FROM invite i WHERE i.business_id = b.id AND i.accepted_at IS NULL
                           AND i.revoked_at IS NULL AND i.expires_at > now()) AS pending_invites
            FROM business b""";

    private final JdbcClient jdbc;

    PlatformBusinessQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserted with SQL so the id is known up front: the owner invite is created for it in the same transaction. */
    public void insert(NewBusiness business) {
        jdbc.sql("""
                        INSERT INTO business (id, slug, legal_name, display_name, default_time_zone, accent_color,
                                              description, contact_email)
                        VALUES (:id, :slug, :legalName, :displayName, :timeZone, :accentColor, :description, :email)""")
                .param("id", business.id())
                .param("slug", business.slug())
                .param("legalName", business.legalName())
                .param("displayName", business.displayName())
                .param("timeZone", business.defaultTimeZone())
                .param("accentColor", business.accentColor())
                .param("description", business.description())
                .param("email", business.contactEmail())
                .update();
    }

    public boolean slugExists(String slug) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM business WHERE slug = :slug)").param("slug", slug)
                .query(Boolean.class).single();
    }

    /** Counts span business-owned tables of every business: call in system scope only. */
    public List<BusinessSummary> summaries() {
        return jdbc.sql(SUMMARY_SQL + " ORDER BY b.created_at DESC").query(SUMMARY_MAPPER).list();
    }

    /** Counts span business-owned tables: call in system scope only. */
    public Optional<BusinessSummary> summary(UUID businessId) {
        return jdbc.sql(SUMMARY_SQL + " WHERE b.id = :id").param("id", businessId).query(SUMMARY_MAPPER).optional();
    }

    private static final RowMapper<BusinessSummary> SUMMARY_MAPPER = (rs, row) -> new BusinessSummary(
            rs.getObject("id", UUID.class),
            rs.getString("slug"),
            rs.getString("display_name"),
            BusinessStatus.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getInt("active_branches"),
            rs.getInt("active_staff"),
            rs.getInt("pending_invites"));
}
