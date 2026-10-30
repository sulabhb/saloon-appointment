package com.salonbooking.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL database for the whole test run.
 *
 * <p>Uses Testcontainers by default. Set {@code TEST_DB_URL}, {@code TEST_DB_ADMIN_USER} and
 * {@code TEST_DB_ADMIN_PASSWORD} to use an existing PostgreSQL 17 instead (for machines without
 * Docker). The admin user must be a superuser; it runs migrations and seeds test data.
 *
 * <p>The application connects as {@link #APP_USER}, a role that is not the table owner and cannot
 * bypass row-level security, exactly like production.
 */
public final class PostgresTestDatabase {

    public static final String APP_USER = "salon_app";
    private static final String APP_PASSWORD = "salon_app";

    private record Server(String url, String adminUser, String adminPassword) {}

    private static final Server SERVER = start();

    private PostgresTestDatabase() {}

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SERVER::url);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", SERVER::adminUser);
        registry.add("spring.flyway.password", SERVER::adminPassword);
        registry.add("spring.flyway.placeholders.app_role", () -> APP_USER);
    }

    /** Superuser connection for seeding and inspecting data. Bypasses row-level security. */
    public static DataSource adminDataSource() {
        return new DriverManagerDataSource(SERVER.url(), SERVER.adminUser(), SERVER.adminPassword());
    }

    private static Server start() {
        String url = System.getenv("TEST_DB_URL");
        Server server;
        if (url != null && !url.isBlank()) {
            server = new Server(url, envOr("TEST_DB_ADMIN_USER", "postgres"), envOr("TEST_DB_ADMIN_PASSWORD", ""));
        } else {
            var container = new PostgreSQLContainer("postgres:17-alpine");
            container.start();
            server = new Server(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }
        createAppRole(server);
        return server;
    }

    private static void createAppRole(Server server) {
        try (Connection connection = DriverManager.getConnection(server.url(), server.adminUser(), server.adminPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '%1$s') THEN
                            CREATE ROLE %1$s;
                        END IF;
                    END
                    $$""".formatted(APP_USER));
            statement.execute("ALTER ROLE %s WITH LOGIN PASSWORD '%s' NOSUPERUSER NOBYPASSRLS"
                    .formatted(APP_USER, APP_PASSWORD));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not prepare the test database", e);
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
