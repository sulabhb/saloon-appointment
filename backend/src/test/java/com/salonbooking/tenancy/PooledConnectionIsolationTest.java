package com.salonbooking.tenancy;

import static com.salonbooking.support.TestData.A_LOCATION_COUNT;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.BUSINESS_B;
import static com.salonbooking.support.TestData.B_LOCATION_COUNT;
import static org.assertj.core.api.Assertions.assertThat;

import com.salonbooking.support.IntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** With a single pooled connection, each borrower must still get only its own business. */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class PooledConnectionIsolationTest extends IntegrationTest {

    private static final String COUNT = "SELECT count(*) FROM location";

    @Autowired
    DataSource dataSource;

    @Test
    void reusedConnectionDoesNotKeepThePreviousBusiness() {
        JdbcTemplate app = new JdbcTemplate(dataSource);

        Integer asA = TenantContext.callForBusiness(BUSINESS_A, () -> app.queryForObject(COUNT, Integer.class));
        Integer unbound = app.queryForObject(COUNT, Integer.class);
        Integer asB = TenantContext.callForBusiness(BUSINESS_B, () -> app.queryForObject(COUNT, Integer.class));
        Integer asSystem = TenantContext.callAsSystem(() -> app.queryForObject(COUNT, Integer.class));
        Integer unboundAfterSystem = app.queryForObject(COUNT, Integer.class);

        assertThat(asA).isEqualTo(A_LOCATION_COUNT);
        assertThat(unbound).isZero();
        assertThat(asB).isEqualTo(B_LOCATION_COUNT);
        assertThat(asSystem).isGreaterThan(A_LOCATION_COUNT + B_LOCATION_COUNT);
        assertThat(unboundAfterSystem).isZero();
    }
}
