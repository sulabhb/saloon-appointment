package com.salonbooking.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * Two complete, live businesses (A and B) plus one in Onboarding (C) and one Suspended (D).
 * Every isolation test checks that business A can never reach business B's rows.
 *
 * <p>Staff accounts all use {@link #PASSWORD}:
 * <ul>
 *   <li>{@link #OWNER_A}: owner of A (all branches)
 *   <li>{@link #RECEPTION_A}: reception at A, Sydney CBD only
 *   <li>{@link #MULTI}: stylist at A (Parramatta) and manager at B (Brisbane)
 *   <li>{@link #FORMER}: membership at A deactivated
 *   <li>{@link #PLATFORM_ADMIN}: platform admin, no business
 * </ul>
 */
public final class TestData {

    public static final String PASSWORD = "correct-horse-battery";
    /** Hashed once: bcrypt is deliberately slow, and data is reseeded before every test. */
    private static final String PASSWORD_HASH = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD);

    public static final String OWNER_A = "owner@glowhair.test";
    public static final String RECEPTION_A = "reception@glowhair.test";
    public static final String MULTI = "alex@example.test";
    public static final String FORMER = "former@glowhair.test";
    /** Platform admin with no business memberships. */
    public static final String PLATFORM_ADMIN = "ops@salonplatform.test";

    public static final UUID BUSINESS_A = UUID.fromString("a0000000-0000-0000-0000-000000000000");
    public static final UUID BUSINESS_B = UUID.fromString("b0000000-0000-0000-0000-000000000000");
    public static final UUID BUSINESS_C = UUID.fromString("c0000000-0000-0000-0000-000000000000");
    public static final UUID BUSINESS_D = UUID.fromString("d0000000-0000-0000-0000-000000000000");

    public static final String SLUG_A = "glow-hair";
    public static final String SLUG_B = "north-cuts";
    public static final String SLUG_C = "new-salon";
    public static final String SLUG_D = "paused-salon";

    public static final UUID A_SYDNEY = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    public static final UUID A_PARRAMATTA = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    public static final UUID A_OLD_BRANCH = UUID.fromString("a0000000-0000-0000-0000-000000000003");
    public static final UUID B_BRISBANE = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    public static final UUID B_NORTH_ONLY = UUID.fromString("b0000000-0000-0000-0000-000000000002");

    /** Locations per business, including inactive ones. */
    public static final int A_LOCATION_COUNT = 3;
    public static final int B_LOCATION_COUNT = 2;
    public static final int ALL_LOCATION_COUNT = 7;

    private TestData() {}

    public static void reset(JdbcTemplate admin) {
        admin.execute("TRUNCATE business, app_user CASCADE");

        insertBusiness(admin, BUSINESS_A, SLUG_A, "Glow Hair", "live", "Australia/Sydney");
        insertBusiness(admin, BUSINESS_B, SLUG_B, "North Cuts", "live", "Australia/Brisbane");
        insertBusiness(admin, BUSINESS_C, SLUG_C, "New Salon", "onboarding", "Australia/Melbourne");
        insertBusiness(admin, BUSINESS_D, SLUG_D, "Paused Salon", "suspended", "Australia/Perth");

        insertLocation(admin, A_SYDNEY, BUSINESS_A, "Sydney CBD", "sydney-cbd", "NSW", "Australia/Sydney", true);
        insertLocation(admin, A_PARRAMATTA, BUSINESS_A, "Parramatta", "parramatta", "NSW", "Australia/Sydney", true);
        insertLocation(admin, A_OLD_BRANCH, BUSINESS_A, "Old Branch", "old-branch", "NSW", "Australia/Sydney", false);
        insertLocation(admin, B_BRISBANE, BUSINESS_B, "Brisbane", "brisbane", "QLD", "Australia/Brisbane", true);
        insertLocation(admin, B_NORTH_ONLY, BUSINESS_B, "North Only", "north-only", "QLD", "Australia/Brisbane", true);
        insertLocation(admin, UUID.randomUUID(), BUSINESS_C, "New Branch", "main", "VIC", "Australia/Melbourne", true);
        insertLocation(admin, UUID.randomUUID(), BUSINESS_D, "Paused Branch", "main", "WA", "Australia/Perth", true);

        insertHours(admin, BUSINESS_A, A_SYDNEY, 1, "09:00", "17:00");
        insertHours(admin, BUSINESS_A, A_SYDNEY, 6, "09:00", "13:00");
        insertHours(admin, BUSINESS_B, B_BRISBANE, 1, "08:00", "18:00");

        UUID owner = insertUser(admin, OWNER_A, "Olivia Owner");
        UUID reception = insertUser(admin, RECEPTION_A, "Riley Reception");
        UUID multi = insertUser(admin, MULTI, "Alex Multi");
        UUID former = insertUser(admin, FORMER, "Frankie Former");

        insertMembership(admin, BUSINESS_A, owner, "owner", true);
        insertMembership(admin, BUSINESS_A, reception, "reception", true, A_SYDNEY);
        insertMembership(admin, BUSINESS_A, multi, "stylist", true, A_PARRAMATTA);
        insertMembership(admin, BUSINESS_B, multi, "manager", true, B_BRISBANE);
        insertMembership(admin, BUSINESS_A, former, "stylist", false, A_SYDNEY);

        UUID platformAdmin = insertUser(admin, PLATFORM_ADMIN, "Pat Platform");
        admin.update("UPDATE app_user SET is_platform_admin = true WHERE id = ?", platformAdmin);
    }

    private static UUID insertUser(JdbcTemplate admin, String email, String name) {
        UUID id = UUID.randomUUID();
        admin.update("INSERT INTO app_user (id, email, full_name, password_hash) VALUES (?, ?, ?, ?)",
                id, email, name, PASSWORD_HASH);
        return id;
    }

    private static void insertMembership(JdbcTemplate admin, UUID businessId, UUID userId, String role, boolean active,
            UUID... locationIds) {
        UUID id = UUID.randomUUID();
        admin.update("INSERT INTO membership (id, business_id, user_id, role, active) VALUES (?, ?, ?, ?, ?)",
                id, businessId, userId, role, active);
        for (UUID locationId : locationIds) {
            admin.update("INSERT INTO membership_location (business_id, membership_id, location_id) VALUES (?, ?, ?)",
                    businessId, id, locationId);
        }
    }

    private static void insertBusiness(JdbcTemplate admin, UUID id, String slug, String name, String status,
            String timeZone) {
        admin.update("""
                INSERT INTO business (id, slug, legal_name, display_name, status, default_time_zone)
                VALUES (?, ?, ?, ?, ?, ?)""", id, slug, name + " Pty Ltd", name, status, timeZone);
    }

    private static void insertLocation(JdbcTemplate admin, UUID id, UUID businessId, String name, String slug,
            String state, String timeZone, boolean active) {
        admin.update("""
                INSERT INTO location (id, business_id, name, slug, address_line1, suburb, state, postcode, time_zone, active)
                VALUES (?, ?, ?, ?, '1 Main Street', ?, ?, '2000', ?, ?)""",
                id, businessId, name, slug, name, state, timeZone, active);
    }

    private static void insertHours(JdbcTemplate admin, UUID businessId, UUID locationId, int weekday, String opens,
            String closes) {
        admin.update("""
                INSERT INTO location_hours (id, business_id, location_id, weekday, opens_at, closes_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?::time, ?::time)""",
                businessId, locationId, weekday, opens, closes);
    }
}
