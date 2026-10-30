package com.salonbooking.identity.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "salon.bootstrap.platform-admin.email=founder@salonplatform.test",
        "salon.bootstrap.platform-admin.password=bootstrap-password-123",
})
class PlatformAdminBootstrapTest extends IntegrationTest {

    @Autowired
    PlatformAdminBootstrap bootstrap;

    @Autowired
    MockMvc mvc;

    private final ApplicationArguments noArgs = new DefaultApplicationArguments();

    @Test
    void createsTheConfiguredPlatformAdminWithAHashedPassword() throws Exception {
        bootstrap.run(noArgs); // Test data is reseeded after start-up, so run it again.

        String hash = admin.queryForObject(
                "SELECT password_hash FROM app_user WHERE email = 'founder@salonplatform.test' AND is_platform_admin",
                String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("bootstrap-password-123");

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(StaffClient.loginBody("founder@salonplatform.test", "bootstrap-password-123", "android", "x")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.user.platformAdmin").value(true));
    }

    @Test
    void runningAgainChangesNothing() {
        bootstrap.run(noArgs);
        bootstrap.run(noArgs);

        assertThat(admin.queryForObject("SELECT count(*) FROM app_user WHERE email = 'founder@salonplatform.test'",
                Integer.class)).isEqualTo(1);
    }
}
