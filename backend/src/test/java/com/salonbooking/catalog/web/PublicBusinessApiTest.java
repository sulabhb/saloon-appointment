package com.salonbooking.catalog.web;

import static com.salonbooking.support.TestData.SLUG_A;
import static com.salonbooking.support.TestData.SLUG_C;
import static com.salonbooking.support.TestData.SLUG_D;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salonbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PublicBusinessApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void liveBusinessShowsItsOwnActiveBranchesOnly() throws Exception {
        mvc.perform(get("/api/v1/public/b/{business}", SLUG_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(SLUG_A))
                .andExpect(jsonPath("$.displayName").value("Glow Hair"))
                .andExpect(jsonPath("$.locations[*].slug", contains("parramatta", "sydney-cbd")));
    }

    @Test
    void branchDetailIncludesOpeningHours() throws Exception {
        mvc.perform(get("/api/v1/public/b/{business}/locations/{location}", SLUG_A, "sydney-cbd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Australia/Sydney"))
                .andExpect(jsonPath("$.hours[*].weekday", contains(1, 6)));
    }

    @Test
    void anotherBusinessesBranchIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/b/{business}/locations/{location}", SLUG_A, "north-only"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void inactiveBranchIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/b/{business}/locations/{location}", SLUG_A, "old-branch"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownBusinessIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/b/{business}", "no-such-salon"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void businessStillOnboardingIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/public/b/{business}", SLUG_C))
                .andExpect(status().isNotFound());
    }

    @Test
    void suspendedBusinessIsUnavailable() throws Exception {
        mvc.perform(get("/api/v1/public/b/{business}", SLUG_D))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_UNAVAILABLE"));
    }
}
