package com.salonbooking.identity.web;

import static com.salonbooking.support.TestData.A_SYDNEY;
import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.RECEPTION_A;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.salonbooking.support.AdminClient;
import com.salonbooking.support.IntegrationTest;
import com.salonbooking.support.StaffClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Owners invite staff with a role and branch access, see the team and remove access. */
class TeamApiTest extends IntegrationTest {

    private static final String NEW_PASSWORD = "new-staff-password";

    @Autowired
    MockMvc mvc;

    AdminClient owner;

    @BeforeEach
    void signIn() throws Exception {
        owner = new AdminClient(mvc, new StaffClient(mvc).androidToken(OWNER_A));
    }

    private String inviteToken(String inviteJson) throws Exception {
        String url = JsonPath.read(owner.post("/api/v1/admin/team/invites", inviteJson)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String accept(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/v1/invites/{token}/accept", token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "%s", "password": "%s", "client": "android"}""".formatted(name, NEW_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void invitedReceptionGetsOnlyTheirBranches() throws Exception {
        String token = inviteToken("""
                {"email": "riley2@glowhair.test", "role": "RECEPTION", "locationIds": ["%s"]}""".formatted(A_SYDNEY));

        AdminClient reception = new AdminClient(mvc, accept(token, "Riley Two"));

        reception.get("/api/v1/auth/me").andExpect(jsonPath("$.selected.role").value("RECEPTION"));
        reception.get("/api/v1/staff/locations").andExpect(jsonPath("$[*].slug", contains("sydney-cbd")));
        owner.get("/api/v1/admin/team/members").andExpect(jsonPath("$[*].email", hasItem("riley2@glowhair.test")));
    }

    @Test
    void invitingAStylistLinksTheirProfile() throws Exception {
        String staffBody = owner.post("/api/v1/admin/staff", """
                        {"displayName": "Sam", "locationIds": ["%s"]}""".formatted(A_SYDNEY))
                .andReturn().getResponse().getContentAsString();
        String staffId = JsonPath.read(staffBody, "$[0].id");

        String token = inviteToken("""
                {"email": "sam@glowhair.test", "role": "STYLIST", "staffId": "%s", "locationIds": ["%s"]}"""
                .formatted(staffId, A_SYDNEY));
        owner.get("/api/v1/admin/staff").andExpect(jsonPath("$[0].login.status").value("INVITED"));

        accept(token, "Sam Lee");

        owner.get("/api/v1/admin/staff")
                .andExpect(jsonPath("$[0].login.status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].login.email").value("sam@glowhair.test"));
        List<String> staffIds = JsonPath.read(owner.get("/api/v1/admin/team/members").andReturn().getResponse()
                .getContentAsString(), "$[?(@.email == 'sam@glowhair.test')].staffId");
        org.assertj.core.api.Assertions.assertThat(staffIds).containsExactly(staffId);
    }

    @Test
    void removingAccessSignsThePersonOut() throws Exception {
        String receptionToken = new StaffClient(mvc).androidToken(RECEPTION_A);
        String members = owner.get("/api/v1/admin/team/members").andReturn().getResponse().getContentAsString();
        String membershipId = JsonPath.<List<String>>read(members, "$[?(@.email == '%s')].id".formatted(RECEPTION_A)).getFirst();

        owner.post("/api/v1/admin/team/members/{id}/deactivate", "", membershipId).andExpect(status().isOk());

        new AdminClient(mvc, receptionToken).get("/api/v1/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void ownersCannotRemoveThemselves() throws Exception {
        String members = owner.get("/api/v1/admin/team/members").andReturn().getResponse().getContentAsString();
        String ownId = JsonPath.<List<String>>read(members, "$[?(@.email == '%s')].id".formatted(OWNER_A)).getFirst();

        owner.post("/api/v1/admin/team/members/{id}/deactivate", "", ownId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANNOT_REMOVE_SELF"));
    }
}
