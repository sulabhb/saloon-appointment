package com.salonbooking.identity.web;

import static com.salonbooking.support.StaffClient.bearer;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.BUSINESS_B;
import static com.salonbooking.support.TestData.BUSINESS_C;
import static com.salonbooking.support.TestData.MULTI;
import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.RECEPTION_A;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The session's business and branch access decide what staff endpoints return. */
class StaffBusinessAccessTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    StaffClient staff;

    @BeforeEach
    void client() {
        staff = new StaffClient(mvc);
    }

    private ResultActions selectBusiness(String token, UUID businessId) throws Exception {
        return mvc.perform(bearer(post("/api/v1/auth/business"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessId\": \"%s\"}".formatted(businessId)));
    }

    private ResultActions locations(String token) throws Exception {
        return mvc.perform(bearer(get("/api/v1/staff/locations"), token));
    }

    @Test
    void ownerSeesEveryActiveBranchOfTheirBusinessOnly() throws Exception {
        locations(staff.androidToken(OWNER_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", contains("parramatta", "sydney-cbd")));
    }

    @Test
    void receptionSeesOnlyAssignedBranches() throws Exception {
        locations(staff.androidToken(RECEPTION_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", contains("sydney-cbd")));
    }

    @Test
    void staffEndpointsNeedASelectedBusiness() throws Exception {
        locations(staff.androidToken(MULTI))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void switchingBusinessSwitchesRoleAndData() throws Exception {
        String token = staff.androidToken(MULTI);

        selectBusiness(token, BUSINESS_B)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected.businessId").value(BUSINESS_B.toString()))
                .andExpect(jsonPath("$.selected.role").value("MANAGER"));
        locations(token).andExpect(jsonPath("$[*].slug", contains("brisbane")));

        selectBusiness(token, BUSINESS_A)
                .andExpect(jsonPath("$.selected.role").value("STYLIST"));
        locations(token).andExpect(jsonPath("$[*].slug", contains("parramatta")));
    }

    @Test
    void cannotSelectABusinessWithoutMembership() throws Exception {
        String token = staff.androidToken(OWNER_A);

        selectBusiness(token, BUSINESS_B).andExpect(status().isNotFound());
        selectBusiness(token, BUSINESS_C).andExpect(status().isNotFound());
        selectBusiness(token, UUID.randomUUID()).andExpect(status().isNotFound());

        locations(token).andExpect(jsonPath("$[*].slug", contains("parramatta", "sydney-cbd")));
    }
}
