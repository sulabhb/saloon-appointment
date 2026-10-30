package com.salonbooking.platform.web;

import static com.salonbooking.support.StaffClient.bearer;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.BUSINESS_B;
import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.PLATFORM_ADMIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PlatformBusinessApiTest extends IntegrationTest {

    static final String NEW_BUSINESS = """
            {"slug": "bloom-studio", "legalName": "Bloom Studio Pty Ltd", "displayName": "Bloom Studio",
             "defaultTimeZone": "Australia/Melbourne", "ownerEmail": "maya@bloomstudio.test",
             "accentColor": "#7A4E8C", "description": "Colour and cuts in Fitzroy."}""";

    @Autowired
    MockMvc mvc;

    String platformAdmin;

    @BeforeEach
    void signIn() throws Exception {
        platformAdmin = new StaffClient(mvc).androidToken(PLATFORM_ADMIN);
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(bearer(post("/api/v1/platform/businesses"), platformAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions changeStatus(String businessId, String status) throws Exception {
        return mvc.perform(bearer(post("/api/v1/platform/businesses/{id}/status", businessId), platformAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"%s\"}".formatted(status)));
    }

    @Test
    void onlyPlatformAdminsCanUseThePlatformApi() throws Exception {
        String owner = new StaffClient(mvc).androidToken(OWNER_A);

        mvc.perform(bearer(get("/api/v1/platform/businesses"), owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/businesses")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsABusinessInOnboardingWithAnOwnerInvite() throws Exception {
        String body = create(NEW_BUSINESS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.business.slug").value("bloom-studio"))
                .andExpect(jsonPath("$.business.status").value("ONBOARDING"))
                .andExpect(jsonPath("$.business.pendingInvites").value(1))
                .andExpect(jsonPath("$.ownerInvite.email").value("maya@bloomstudio.test"))
                .andExpect(jsonPath("$.ownerInvite.role").value("OWNER"))
                .andExpect(jsonPath("$.ownerInvite.url", startsWith("http://localhost:8080/invite/")))
                .andReturn().getResponse().getContentAsString();

        // Not reachable by customers until it goes live (TEN-3).
        mvc.perform(get("/api/v1/public/b/bloom-studio")).andExpect(status().isNotFound());

        String id = JsonPath.read(body, "$.business.id");
        mvc.perform(bearer(get("/api/v1/platform/businesses/{id}", id), platformAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invites", hasSize(1)))
                .andExpect(jsonPath("$.invites[0].status").value("PENDING"));
    }

    @Test
    void rejectsTakenReservedAndInvalidInput() throws Exception {
        create(NEW_BUSINESS).andExpect(status().isCreated());
        create(NEW_BUSINESS).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SLUG_TAKEN"));
        create(NEW_BUSINESS.replace("bloom-studio", "glow-hair")).andExpect(status().isConflict());
        create(NEW_BUSINESS.replace("bloom-studio", "admin")).andExpect(status().isConflict());
        create(NEW_BUSINESS.replace("bloom-studio", "Bad Slug")).andExpect(status().isBadRequest());
        create(NEW_BUSINESS.replace("bloom-studio", "x-salon").replace("Australia/Melbourne", "Mars/Olympus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsBusinessesWithCounts() throws Exception {
        String body = mvc.perform(bearer(get("/api/v1/platform/businesses"), platformAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andReturn().getResponse().getContentAsString();

        // Filter expressions return lists, one entry per matching business.
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.slug == 'glow-hair')].activeBranches")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.slug == 'glow-hair')].activeStaff")).containsExactly(3);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.slug == 'north-cuts')].activeStaff")).containsExactly(1);
    }

    @Test
    void lifecycleControlsPublicBookingAndStaffAccess() throws Exception {
        String owner = new StaffClient(mvc).androidToken(OWNER_A);
        String a = BUSINESS_A.toString();

        changeStatus(a, "SUSPENDED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/public/b/glow-hair")).andExpect(status().isServiceUnavailable());

        changeStatus(a, "LIVE").andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/b/glow-hair")).andExpect(status().isOk());

        changeStatus(a, "CLOSED").andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/b/glow-hair")).andExpect(status().isNotFound());
        mvc.perform(bearer(get("/api/v1/staff/locations"), owner)).andExpect(status().isUnauthorized());

        changeStatus(a, "LIVE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_CHANGE"));
    }

    @Test
    void resendingAnOwnerInviteReplacesTheOpenOne() throws Exception {
        String created = create(NEW_BUSINESS).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.business.id");
        String firstUrl = JsonPath.read(created, "$.ownerInvite.url");

        mvc.perform(bearer(post("/api/v1/platform/businesses/{id}/owner-invites", id), platformAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"maya@bloomstudio.test\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/invites/{token}", token(firstUrl))).andExpect(status().isGone());
        mvc.perform(bearer(get("/api/v1/platform/businesses/{id}", id), platformAdmin))
                .andExpect(jsonPath("$.invites[*].status").value(org.hamcrest.Matchers.contains("PENDING", "REVOKED")));
    }

    @Test
    void revokedInviteCannotBeUsed() throws Exception {
        String created = create(NEW_BUSINESS).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.business.id");
        String inviteId = JsonPath.read(created, "$.ownerInvite.id");

        mvc.perform(bearer(delete("/api/v1/platform/businesses/{id}/invites/{inviteId}", id, inviteId), platformAdmin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/invites/{token}", token(JsonPath.read(created, "$.ownerInvite.url"))))
                .andExpect(status().isGone());
    }

    @Test
    void invitesOfOneBusinessAreNotVisibleThroughAnother() throws Exception {
        String created = create(NEW_BUSINESS).andReturn().getResponse().getContentAsString();
        String inviteId = JsonPath.read(created, "$.ownerInvite.id");

        mvc.perform(bearer(get("/api/v1/platform/businesses/{id}", BUSINESS_B), platformAdmin))
                .andExpect(jsonPath("$.invites", hasSize(0)));
        mvc.perform(bearer(delete("/api/v1/platform/businesses/{id}/invites/{inviteId}", BUSINESS_B, inviteId),
                        platformAdmin))
                .andExpect(status().isNotFound());
    }

    static String token(String inviteUrl) {
        return inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);
    }
}
