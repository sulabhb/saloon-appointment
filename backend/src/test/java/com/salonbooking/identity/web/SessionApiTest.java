package com.salonbooking.identity.web;

import static com.salonbooking.support.StaffClient.bearer;
import static com.salonbooking.support.TestData.MULTI;
import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.RECEPTION_A;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Session lifetime: listing, revoking, expiry and removed access. */
class SessionApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    StaffClient staff;

    @BeforeEach
    void client() {
        staff = new StaffClient(mvc);
    }

    @Test
    void personCanSeeAndRevokeTheirOtherSessions() throws Exception {
        String phone = staff.androidToken(OWNER_A, "Pixel");
        String tablet = staff.androidToken(OWNER_A, "Tablet");

        String sessions = mvc.perform(bearer(get("/api/v1/auth/sessions"), phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].deviceName", containsInAnyOrder("Pixel", "Tablet")))
                .andReturn().getResponse().getContentAsString();
        List<String> tabletIds = JsonPath.read(sessions, "$[?(@.deviceName == 'Tablet')].id");

        mvc.perform(bearer(delete("/api/v1/auth/sessions/{id}", tabletIds.getFirst()), phone))
                .andExpect(status().isNoContent());

        mvc.perform(bearer(get("/api/v1/auth/me"), tablet)).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get("/api/v1/auth/me"), phone)).andExpect(status().isOk());
        mvc.perform(bearer(get("/api/v1/auth/sessions"), phone))
                .andExpect(jsonPath("$[*].deviceName", contains("Pixel")))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void cannotRevokeSomeoneElsesSession() throws Exception {
        String owner = staff.androidToken(OWNER_A);
        String reception = staff.androidToken(RECEPTION_A);
        String receptionSessionId = JsonPath.read(
                mvc.perform(bearer(get("/api/v1/auth/sessions"), reception)).andReturn().getResponse().getContentAsString(),
                "$[0].id");

        mvc.perform(bearer(delete("/api/v1/auth/sessions/{id}", receptionSessionId), owner))
                .andExpect(status().isNotFound());
        mvc.perform(bearer(get("/api/v1/auth/me"), reception)).andExpect(status().isOk());
    }

    @Test
    void idleSessionExpires() throws Exception {
        String token = staff.androidToken(OWNER_A);

        admin.update("UPDATE auth_session SET idle_expires_at = now() - interval '1 second'");

        mvc.perform(bearer(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionStopsWorkingWhenAccessIsRemoved() throws Exception {
        String token = staff.androidToken(RECEPTION_A);
        mvc.perform(bearer(get("/api/v1/staff/locations"), token)).andExpect(status().isOk());

        admin.update("UPDATE membership SET active = false WHERE user_id = (SELECT id FROM app_user WHERE email = ?)",
                RECEPTION_A);

        mvc.perform(bearer(get("/api/v1/staff/locations"), token)).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAccountIsSignedOutEverywhere() throws Exception {
        String token = staff.androidToken(MULTI);

        admin.update("UPDATE app_user SET active = false WHERE email = ?", MULTI);

        mvc.perform(bearer(get("/api/v1/auth/me"), token)).andExpect(status().isUnauthorized());
    }
}
