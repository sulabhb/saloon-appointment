package com.salonbooking.identity.web;

import static com.salonbooking.support.StaffClient.bearer;
import static com.salonbooking.support.StaffClient.loginBody;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.FORMER;
import static com.salonbooking.support.TestData.MULTI;
import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class SignInApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    private ResultActions login(String email, String password, String client) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password, client, "Test device")));
    }

    @Test
    void androidSignInReturnsATokenAndSelectsTheOnlyBusiness() throws Exception {
        String body = login(OWNER_A, PASSWORD, "android")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.me.user.email").value(OWNER_A))
                .andExpect(jsonPath("$.me.businesses", hasSize(1)))
                .andExpect(jsonPath("$.me.selected.businessId").value(BUSINESS_A.toString()))
                .andExpect(jsonPath("$.me.selected.role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.token");

        mvc.perform(bearer(get("/api/v1/auth/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.fullName").value("Olivia Owner"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String wrongPassword = login(OWNER_A, "not-the-password", "android")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@glowhair.test", PASSWORD, "android")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).contains("INVALID_CREDENTIALS");
    }

    @Test
    void emailIsNotCaseSensitive() throws Exception {
        login("Owner@GlowHair.TEST", PASSWORD, "android").andExpect(status().isOk());
    }

    @Test
    void accountLocksAfterRepeatedFailuresAndUnlocksLater() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(OWNER_A, "wrong-" + i, "android").andExpect(status().isUnauthorized());
        }
        login(OWNER_A, PASSWORD, "android").andExpect(status().isUnauthorized());

        admin.update("UPDATE app_user SET locked_until = now() - interval '1 minute' WHERE email = ?", OWNER_A);

        login(OWNER_A, PASSWORD, "android").andExpect(status().isOk());
    }

    @Test
    void accountWithoutActiveMembershipCannotSignIn() throws Exception {
        login(FORMER, PASSWORD, "android")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NO_BUSINESS_ACCESS"));
    }

    @Test
    void personInSeveralBusinessesChoosesOneAfterSignIn() throws Exception {
        login(MULTI, PASSWORD, "android")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.businesses[*].slug").value(org.hamcrest.Matchers.contains("glow-hair", "north-cuts")))
                .andExpect(jsonPath("$.me.selected").value(nullValue()));
    }

    @Test
    void invalidRequestIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"owner@glowhair.test\", \"password\": \"x\", \"client\": \"ios\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        String token = new StaffClient(mvc).androidToken(OWNER_A);

        byte[] stored = admin.queryForObject("SELECT token_hash FROM auth_session", byte[].class);
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        assertThat(stored).isEqualTo(expected);
    }

    @Test
    void webSignInUsesASecureHttpOnlyCookieAndNoTokenInTheBody() throws Exception {
        MvcResult result = login(OWNER_A, PASSWORD, "web")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("SALON_SESSION=").contains("HttpOnly", "Secure", "SameSite=Lax");

        Cookie session = result.getResponse().getCookie("SALON_SESSION");
        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void cookieSessionsNeedACsrfTokenForChanges() throws Exception {
        Cookie session = login(OWNER_A, PASSWORD, "web").andReturn().getResponse().getCookie("SALON_SESSION");

        mvc.perform(post("/api/v1/auth/logout").cookie(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").cookie(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void bearerSessionsDoNotNeedCsrf() throws Exception {
        String token = new StaffClient(mvc).androidToken(OWNER_A);

        mvc.perform(bearer(post("/api/v1/auth/logout"), token)).andExpect(status().isNoContent());
        mvc.perform(bearer(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutASessionAreUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(bearer(get("/api/v1/auth/me"), "made-up-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicBookingIgnoresStaffTokens() throws Exception {
        mvc.perform(bearer(get("/api/v1/public/b/glow-hair"), "made-up-token")).andExpect(status().isOk());
    }
}
