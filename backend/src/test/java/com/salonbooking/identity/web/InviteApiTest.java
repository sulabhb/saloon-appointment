package com.salonbooking.identity.web;

import static com.salonbooking.support.StaffClient.bearer;
import static com.salonbooking.support.TestData.MULTI;
import static com.salonbooking.support.TestData.PASSWORD;
import static com.salonbooking.support.TestData.PLATFORM_ADMIN;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Accepting an owner invite: new accounts, existing accounts, and links that no longer work. */
class InviteApiTest extends IntegrationTest {

    private static final String NEW_OWNER_PASSWORD = "bloom-owner-password";

    @Autowired
    MockMvc mvc;

    String businessId;
    String inviteToken;

    @BeforeEach
    void createBusiness() throws Exception {
        inviteBusiness("maya@bloomstudio.test");
    }

    private void inviteBusiness(String ownerEmail) throws Exception {
        String platformAdmin = new StaffClient(mvc).androidToken(PLATFORM_ADMIN);
        admin.update("DELETE FROM invite");
        admin.update("DELETE FROM business WHERE slug = 'bloom-studio'");
        String created = mvc.perform(bearer(post("/api/v1/platform/businesses"), platformAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.salonbooking.platform.web.PlatformBusinessApiTestData.newBusiness(ownerEmail)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        businessId = JsonPath.read(created, "$.business.id");
        String url = JsonPath.read(created, "$.ownerInvite.url");
        inviteToken = url.substring(url.lastIndexOf('/') + 1);
    }

    private ResultActions accept(String fullName, String password) throws Exception {
        String name = fullName == null ? "null" : "\"" + fullName + "\"";
        return mvc.perform(post("/api/v1/invites/{token}/accept", inviteToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": %s, "password": "%s", "client": "android", "deviceName": "Maya's phone"}"""
                        .formatted(name, password)));
    }

    @Test
    void previewShowsTheBusinessAndWhetherAnAccountExists() throws Exception {
        mvc.perform(get("/api/v1/invites/{token}", inviteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Bloom Studio"))
                .andExpect(jsonPath("$.email").value("maya@bloomstudio.test"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.accountExists").value(false));
    }

    @Test
    void newOwnerCreatesAnAccountAndIsSignedInToTheBusiness() throws Exception {
        String body = accept("Maya Chen", NEW_OWNER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.me.user.fullName").value("Maya Chen"))
                .andExpect(jsonPath("$.me.selected.businessId").value(businessId))
                .andExpect(jsonPath("$.me.selected.role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();

        // The new owner works in their business only: it has no branches yet, and others' are invisible.
        mvc.perform(bearer(get("/api/v1/staff/locations"), JsonPath.read(body, "$.token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // And can sign in normally afterwards.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(StaffClient.loginBody("maya@bloomstudio.test", NEW_OWNER_PASSWORD, "android", "x")))
                .andExpect(status().isOk());
    }

    @Test
    void inviteWorksOnlyOnce() throws Exception {
        accept("Maya Chen", NEW_OWNER_PASSWORD).andExpect(status().isOk());

        accept("Someone Else", "another-long-password")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INVITE_EXPIRED"));
        mvc.perform(get("/api/v1/invites/{token}", inviteToken)).andExpect(status().isGone());
    }

    @Test
    void newAccountsNeedANameAndALongPassword() throws Exception {
        accept(null, NEW_OWNER_PASSWORD).andExpect(status().isBadRequest());
        accept("Maya Chen", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_TOO_SHORT"));
        // Failed attempts do not use up the invite.
        accept("Maya Chen", NEW_OWNER_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void existingAccountConfirmsWithItsPasswordAndGainsTheBusiness() throws Exception {
        inviteBusiness(MULTI);
        mvc.perform(get("/api/v1/invites/{token}", inviteToken)).andExpect(jsonPath("$.accountExists").value(true));

        accept(null, "not-their-password").andExpect(status().isUnauthorized());
        accept(null, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.user.fullName").value("Alex Multi"))
                .andExpect(jsonPath("$.me.businesses", hasSize(3)))
                .andExpect(jsonPath("$.me.selected.businessId").value(businessId));
    }

    @Test
    void expiredInviteCannotBeUsed() throws Exception {
        admin.update("UPDATE invite SET expires_at = now() - interval '1 minute'");

        mvc.perform(get("/api/v1/invites/{token}", inviteToken)).andExpect(status().isGone());
        accept("Maya Chen", NEW_OWNER_PASSWORD).andExpect(status().isGone());
    }

    @Test
    void unknownTokenIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/invites/{token}", "not-a-real-token")).andExpect(status().isNotFound());
    }

    @Test
    void webAcceptSetsTheSessionCookie() throws Exception {
        mvc.perform(post("/api/v1/invites/{token}/accept", inviteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Maya Chen", "password": "%s", "client": "web"}""".formatted(NEW_OWNER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getCookie("SALON_SESSION")).isNotNull());
    }
}
