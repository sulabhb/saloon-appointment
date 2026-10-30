package com.salonbooking.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Full application against real PostgreSQL, with {@link TestData} freshly seeded per test. */
// The outbox scheduler is off; tests run the dispatcher themselves.
@SpringBootTest(properties = "salon.notifications.scheduler-enabled=false")
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    protected final JdbcTemplate admin = new JdbcTemplate(PostgresTestDatabase.adminDataSource());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void seedTestData() {
        TestData.reset(admin);
    }
}
