package com.salonbooking.identity.web;

import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Forgotten staff passwords (DSH-1). */
class PasswordResetApiTest extends IntegrationTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("salon.notifications.delivery", () -> "smtp");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
        registry.add("salon.auth.app-base-url", () -> "https://app.example.test");
    }

    static final Pattern LINK = Pattern.compile("https://app\\.example\\.test/reset-password/([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;

    ResultActions requestReset(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    ResultActions confirm(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, password)));
    }

    String emailedToken() {
        await().atMost(Duration.ofSeconds(5)).until(() -> smtp.getReceivedMessages().length == 1);
        Matcher m = LINK.matcher(GreenMailUtil.getBody(smtp.getReceivedMessages()[0]).replace("=\r\n", ""));
        assertThat(m.find()).as("reset link in email").isTrue();
        return m.group(1);
    }

    @Test
    void aResetLinkSetsANewPasswordAndSignsOutEverywhere() throws Exception {
        String oldSession = new StaffClient(mvc).androidToken(OWNER_A);
        String unknown = requestReset("nobody@example.test").andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String known = requestReset(OWNER_A.toUpperCase()).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(known).isEqualTo(unknown);

        String token = emailedToken();
        assertThat(smtp.getReceivedMessages()[0].getSubject()).isEqualTo("Reset your password");

        confirm(token, "short").andExpect(status().isBadRequest());
        confirm(token, "a-brand-new-password").andExpect(status().isNoContent());
        confirm(token, "another-new-password").andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("LINK_EXPIRED"));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldSession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\", \"client\": \"android\"}".formatted(OWNER_A, PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"a-brand-new-password\", \"client\": \"android\"}".formatted(OWNER_A)))
                .andExpect(status().isOk());
    }

    @Test
    void aNewRequestReplacesTheEarlierLinkAndLinksExpire() throws Exception {
        requestReset(OWNER_A);
        String first = emailedToken();
        smtp.purgeEmailFromAllMailboxes();
        requestReset(OWNER_A);
        String second = emailedToken();

        confirm(first, "a-brand-new-password").andExpect(status().isGone());
        admin.update("UPDATE password_reset SET expires_at = now() - interval '1 minute' WHERE used_at IS NULL");
        confirm(second, "a-brand-new-password").andExpect(status().isGone());
    }

    @Test
    void unknownAddressesGetNoEmail() throws Exception {
        requestReset("nobody@example.test").andExpect(status().isAccepted());
        Thread.sleep(300);
        assertThat(smtp.getReceivedMessages()).isEmpty();
        assertThat(admin.queryForObject("SELECT count(*) FROM password_reset", Integer.class)).isZero();
    }
}
