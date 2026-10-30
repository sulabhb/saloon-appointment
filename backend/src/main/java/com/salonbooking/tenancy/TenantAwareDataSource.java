package com.salonbooking.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Copies {@link TenantContext} into PostgreSQL session settings each time a connection is handed
 * out, so row-level security policies see the current business.
 *
 * <p>Both settings are always overwritten, so a pooled connection never keeps the previous
 * user's business. Relies on the pool's auto-commit being on: the settings are applied before
 * any transaction starts, so a rollback cannot undo them.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final String APPLY_TENANT_SQL =
            "select set_config('app.business_id', ?, false), set_config('app.system', ?, false)";

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return applyTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return applyTenant(super.getConnection(username, password));
    }

    private static Connection applyTenant(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(APPLY_TENANT_SQL)) {
            statement.setString(1, TenantContext.currentBusinessId().map(UUID::toString).orElse(""));
            statement.setString(2, TenantContext.isSystem() ? "on" : "off");
            statement.execute();
            return connection;
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }
}
