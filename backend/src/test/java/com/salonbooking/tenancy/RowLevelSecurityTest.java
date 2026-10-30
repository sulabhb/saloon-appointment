package com.salonbooking.tenancy;

import static com.salonbooking.support.TestData.A_LOCATION_COUNT;
import static com.salonbooking.support.TestData.ALL_LOCATION_COUNT;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.BUSINESS_B;
import static com.salonbooking.support.TestData.B_BRISBANE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.salonbooking.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database layer of tenant isolation: plain SQL through the application's connection pool, with
 * no business filter in the queries, still only sees the bound business.
 */
class RowLevelSecurityTest extends IntegrationTest {

    @Autowired
    DataSource dataSource;

    JdbcTemplate app() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    void applicationRoleCannotBypassRowLevelSecurity() {
        assertThat(dataSource).isInstanceOf(TenantAwareDataSource.class);

        Boolean superuser = app().queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);
        Boolean bypassRls = app().queryForObject("SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
        Integer ownedTables = app().queryForObject(
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tableowner = current_user", Integer.class);

        assertThat(superuser).isFalse();
        assertThat(bypassRls).isFalse();
        assertThat(ownedTables).isZero();
    }

    @Test
    void unfilteredQuerySeesOnlyTheBoundBusiness() {
        List<UUID> owners = TenantContext.callForBusiness(BUSINESS_A,
                () -> app().queryForList("SELECT business_id FROM location", UUID.class));

        assertThat(owners).hasSize(A_LOCATION_COUNT).containsOnly(BUSINESS_A);
    }

    @Test
    void nothingIsVisibleWithoutABusiness() {
        Integer count = app().queryForObject("SELECT count(*) FROM location", Integer.class);

        assertThat(count).isZero();
    }

    @Test
    void cannotInsertRowsForAnotherBusiness() {
        assertThatThrownBy(() -> TenantContext.runForBusiness(BUSINESS_A, () -> app().update("""
                INSERT INTO location (id, business_id, name, slug, address_line1, suburb, state, postcode, time_zone)
                VALUES (gen_random_uuid(), ?, 'Sneaky', 'sneaky', '1 St', 'Sydney', 'NSW', '2000', 'Australia/Sydney')""",
                BUSINESS_B)))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("violates row-level security policy");
    }

    @Test
    void cannotUpdateOrDeleteAnotherBusinessesRows() {
        int updated = TenantContext.callForBusiness(BUSINESS_A,
                () -> app().update("UPDATE location SET name = 'Taken over' WHERE id = ?", B_BRISBANE));
        int deleted = TenantContext.callForBusiness(BUSINESS_A,
                () -> app().update("DELETE FROM location WHERE id = ?", B_BRISBANE));

        assertThat(updated).isZero();
        assertThat(deleted).isZero();
        assertThat(admin.queryForObject("SELECT name FROM location WHERE id = ?", String.class, B_BRISBANE))
                .isEqualTo("Brisbane");
    }

    @Test
    void systemScopeSeesEveryBusiness() {
        Integer count = TenantContext.callAsSystem(
                () -> app().queryForObject("SELECT count(*) FROM location", Integer.class));

        assertThat(count).isEqualTo(ALL_LOCATION_COUNT);
    }
}
