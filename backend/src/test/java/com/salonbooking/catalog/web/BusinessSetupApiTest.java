package com.salonbooking.catalog.web;

import static com.salonbooking.support.TestData.A_PARRAMATTA;
import static com.salonbooking.support.TestData.A_SYDNEY;
import static com.salonbooking.support.TestData.BUSINESS_B;
import static com.salonbooking.support.TestData.B_BRISBANE;
import static com.salonbooking.support.TestData.B_NORTH_ONLY;
import static com.salonbooking.support.TestData.MULTI;
import static com.salonbooking.support.TestData.OWNER_A;
import static com.salonbooking.support.TestData.RECEPTION_A;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.test.web.servlet.MockMvc;

/** Owners set up branches, services and staff; managers only their branches; nothing crosses businesses. */
class BusinessSetupApiTest extends IntegrationTest {

    static final String NEW_BRANCH = """
            {"name": "Bondi", "slug": "bondi", "addressLine1": "2 Campbell Parade", "suburb": "Bondi Beach",
             "state": "NSW", "postcode": "2026", "phone": "02 9000 0001", "timeZone": "Australia/Sydney"}""";

    @Autowired
    MockMvc mvc;

    AdminClient owner;

    @BeforeEach
    void signIn() throws Exception {
        owner = new AdminClient(mvc, new StaffClient(mvc).androidToken(OWNER_A));
    }

    private String createCategory(String name) throws Exception {
        String body = owner.post("/api/v1/admin/categories", "{\"name\": \"%s\"}".formatted(name))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$[?(@.name == '%s')].id".formatted(name)).getFirst();
    }

    private String createService(String categoryId, String name, String... offerAt) throws Exception {
        String locations = String.join("\",\"", offerAt);
        String body = owner.post("/api/v1/admin/services", """
                        {"categoryId": "%s", "name": "%s", "durationMin": 45, "bufferAfterMin": 10, "priceCents": 8500,
                         "priceIsFrom": false, "offerAtLocationIds": [%s]}"""
                        .formatted(categoryId, name, offerAt.length == 0 ? "" : "\"" + locations + "\""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[*].services[?(@.name == '%s')].id".formatted(name));
        return ids.getFirst();
    }

    @Test
    void ownerCreatesAndEditsABranch() throws Exception {
        String id = JsonPath.read(owner.post("/api/v1/admin/locations", NEW_BRANCH)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        owner.get("/api/v1/admin/locations")
                .andExpect(jsonPath("$[*].slug", hasItem("bondi")));
        owner.put("/api/v1/admin/locations/{id}", NEW_BRANCH.replace("\"Bondi\"", "\"Bondi Beach\"")
                        .replace("}", ", \"active\": false}"), id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bondi Beach"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void branchInputIsValidated() throws Exception {
        owner.post("/api/v1/admin/locations", NEW_BRANCH).andExpect(status().isCreated());
        owner.post("/api/v1/admin/locations", NEW_BRANCH)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SLUG_TAKEN"));
        owner.post("/api/v1/admin/locations", NEW_BRANCH.replace("bondi\"", "bondi-2\"").replace("Australia/Sydney", "Moon/Base"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openingHoursAreReplacedAndMustNotOverlap() throws Exception {
        owner.put("/api/v1/admin/locations/{id}/hours", """
                        {"hours": [{"weekday": 2, "opensAt": "09:00", "closesAt": "12:30"},
                                   {"weekday": 2, "opensAt": "13:30", "closesAt": "18:00"},
                                   {"weekday": 4, "opensAt": "09:00", "closesAt": "21:00"}]}""", A_SYDNEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hours", hasSize(3)))
                .andExpect(jsonPath("$.hours[0].opensAt").value("09:00:00"));

        owner.put("/api/v1/admin/locations/{id}/hours", """
                        {"hours": [{"weekday": 2, "opensAt": "09:00", "closesAt": "13:00"},
                                   {"weekday": 2, "opensAt": "12:00", "closesAt": "18:00"}]}""", A_SYDNEY)
                .andExpect(status().isBadRequest());
        owner.put("/api/v1/admin/locations/{id}/hours", """
                        {"hours": [{"weekday": 3, "opensAt": "18:00", "closesAt": "09:00"}]}""", A_SYDNEY)
                .andExpect(status().isBadRequest());
    }

    @Test
    void closuresCanBeAddedAndRemoved() throws Exception {
        String body = owner.post("/api/v1/admin/locations/{id}/closures", """
                        {"startsOn": "2030-12-25", "endsOn": "2030-12-26", "reason": "Christmas"}""", A_SYDNEY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closures[0].reason").value("Christmas"))
                .andReturn().getResponse().getContentAsString();
        String closureId = JsonPath.read(body, "$.closures[0].id");

        owner.delete("/api/v1/admin/locations/{id}/closures/{closureId}", A_SYDNEY, closureId)
                .andExpect(status().isNoContent());
        owner.get("/api/v1/admin/locations/{id}", A_SYDNEY).andExpect(jsonPath("$.closures", empty()));
    }

    @Test
    void servicesAreCreatedInCategoriesAndOfferedPerBranch() throws Exception {
        String hair = createCategory("Hair");
        String serviceId = createService(hair, "Wash and blow-dry", A_SYDNEY.toString());

        owner.get("/api/v1/admin/catalog")
                .andExpect(jsonPath("$[0].name").value("Hair"))
                .andExpect(jsonPath("$[0].services[0].priceCents").value(8500))
                .andExpect(jsonPath("$[0].services[0].branchCount").value(1));
        owner.get("/api/v1/admin/locations/{id}", A_SYDNEY)
                .andExpect(jsonPath("$.services[0].offered").value(true));
        owner.get("/api/v1/admin/locations/{id}", A_PARRAMATTA)
                .andExpect(jsonPath("$.services[0].offered").value(false));

        owner.put("/api/v1/admin/locations/{id}/services/{serviceId}",
                        "{\"offered\": true, \"priceCents\": 9500, \"durationMin\": 50}", A_PARRAMATTA, serviceId)
                .andExpect(jsonPath("$.services[0].offered").value(true))
                .andExpect(jsonPath("$.services[0].priceCents").value(9500));
        owner.put("/api/v1/admin/locations/{id}/services/{serviceId}", "{\"offered\": false}", A_SYDNEY, serviceId)
                .andExpect(jsonPath("$.services[0].offered").value(false));
    }

    @Test
    void staffProfilesLinkToBranchesAndServices() throws Exception {
        String serviceId = createService(createCategory("Colour"), "Full colour", A_SYDNEY.toString());

        owner.post("/api/v1/admin/staff", """
                        {"displayName": "Sam", "bio": "Colour specialist", "locationIds": ["%s"], "serviceIds": ["%s"]}"""
                        .formatted(A_SYDNEY, serviceId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].displayName").value("Sam"))
                .andExpect(jsonPath("$[0].bookableOnline").value(true))
                .andExpect(jsonPath("$[0].locationIds", contains(A_SYDNEY.toString())))
                .andExpect(jsonPath("$[0].serviceIds", contains(serviceId)));
    }

    @Test
    void setupChecklistTracksWhatIsMissing() throws Exception {
        owner.get("/api/v1/admin/business")
                .andExpect(jsonPath("$.profile.displayName").value("Glow Hair"))
                .andExpect(jsonPath("$.setup.branchWithHours").value(true))
                .andExpect(jsonPath("$.setup.serviceOffered").value(false))
                .andExpect(jsonPath("$.setup.bookableStaff").value(false));

        String serviceId = createService(createCategory("Hair"), "Cut", A_SYDNEY.toString());
        String staffId = JsonPath.read(owner.post("/api/v1/admin/staff", """
                {"displayName": "Sam", "locationIds": ["%s"], "serviceIds": ["%s"]}""".formatted(A_SYDNEY, serviceId))
                .andReturn().getResponse().getContentAsString(), "$[0].id");

        // Someone who can do the work but has no shifts cannot be booked yet.
        owner.get("/api/v1/admin/business")
                .andExpect(jsonPath("$.setup.serviceOffered").value(true))
                .andExpect(jsonPath("$.setup.bookableStaff").value(false));
        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", """
                {"shifts": [{"weekday": 1, "locationId": "%s", "startsAt": "09:00", "endsAt": "17:00"}]}"""
                .formatted(A_SYDNEY), staffId).andExpect(status().isOk());

        owner.get("/api/v1/admin/business")
                .andExpect(jsonPath("$.setup.bookableStaff").value(true))
                // Glow Hair is already live, so there is nothing to go live with.
                .andExpect(jsonPath("$.setup.canGoLive").value(false));
    }

    @Test
    void newOwnerSetsUpTheirBusinessAndTakesItLive() throws Exception {
        AdminClient platform = new AdminClient(mvc, new StaffClient(mvc).androidToken(com.salonbooking.support.TestData.PLATFORM_ADMIN));
        String created = platform.post("/api/v1/platform/businesses",
                        com.salonbooking.platform.web.PlatformBusinessApiTestData.newBusiness("maya@bloom.test"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(created, "$.ownerInvite.url");
        String ownerToken = JsonPath.read(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/invites/{token}/accept", url.substring(url.lastIndexOf('/') + 1))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Maya\", \"password\": \"bloom-owner-password\", \"client\": \"android\"}"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        owner = new AdminClient(mvc, ownerToken);

        owner.post("/api/v1/admin/business/go-live", "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SETUP_INCOMPLETE"));

        String branchId = JsonPath.read(owner.post("/api/v1/admin/locations", NEW_BRANCH)
                .andReturn().getResponse().getContentAsString(), "$.id");
        owner.put("/api/v1/admin/locations/{id}/hours",
                "{\"hours\": [{\"weekday\": 1, \"opensAt\": \"09:00\", \"closesAt\": \"17:00\"}]}", branchId);
        String serviceId = createService(createCategory("Hair"), "Cut", branchId);
        String staffId = JsonPath.read(owner.post("/api/v1/admin/staff", """
                {"displayName": "Maya", "locationIds": ["%s"], "serviceIds": ["%s"]}""".formatted(branchId, serviceId))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        owner.put("/api/v1/admin/staff/{id}/schedule/weekly", """
                {"shifts": [{"weekday": 1, "locationId": "%s", "startsAt": "09:00", "endsAt": "17:00"}]}"""
                .formatted(branchId), staffId).andExpect(status().isOk());

        owner.get("/api/v1/admin/business").andExpect(jsonPath("$.setup.canGoLive").value(true));
        owner.post("/api/v1/admin/business/go-live", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.status").value("LIVE"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/public/b/bloom-studio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations[*].slug", contains("bondi")));
    }

    @Test
    void ownerUpdatesTheBusinessProfile() throws Exception {
        owner.put("/api/v1/admin/business", """
                        {"displayName": "Glow Hair Studio", "description": "Sydney colour experts", "accentColor": "#7A4E8C"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.displayName").value("Glow Hair Studio"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/public/b/glow-hair"))
                .andExpect(jsonPath("$.accentColor").value("#7A4E8C"));
    }

    @Test
    void receptionCannotUseSetup() throws Exception {
        AdminClient reception = new AdminClient(mvc, new StaffClient(mvc).androidToken(RECEPTION_A));

        reception.get("/api/v1/admin/locations").andExpect(status().isForbidden());
        reception.post("/api/v1/admin/locations", NEW_BRANCH).andExpect(status().isForbidden());
    }

    @Test
    void managerChangesOnlyTheirOwnBranches() throws Exception {
        String token = new StaffClient(mvc).androidToken(MULTI);
        AdminClient manager = new AdminClient(mvc, token);
        manager.post("/api/v1/auth/business", "{\"businessId\": \"%s\"}".formatted(BUSINESS_B)).andExpect(status().isOk());
        String hours = "{\"hours\": [{\"weekday\": 1, \"opensAt\": \"10:00\", \"closesAt\": \"16:00\"}]}";

        manager.get("/api/v1/admin/locations").andExpect(jsonPath("$[*].slug", contains("brisbane")));
        manager.put("/api/v1/admin/locations/{id}/hours", hours, B_BRISBANE).andExpect(status().isOk());
        manager.put("/api/v1/admin/locations/{id}/hours", hours, B_NORTH_ONLY).andExpect(status().isForbidden());
        manager.post("/api/v1/admin/locations", NEW_BRANCH).andExpect(status().isForbidden());
        manager.post("/api/v1/admin/categories", "{\"name\": \"Hair\"}").andExpect(status().isForbidden());
    }

    @Test
    void anotherBusinessesBranchesCannotBeReachedOrLinked() throws Exception {
        owner.get("/api/v1/admin/locations/{id}", B_BRISBANE).andExpect(status().isNotFound());
        owner.put("/api/v1/admin/locations/{id}/hours", "{\"hours\": []}", B_BRISBANE).andExpect(status().isNotFound());
        owner.post("/api/v1/admin/locations/{id}/closures", "{\"startsOn\": \"2030-01-01\", \"endsOn\": \"2030-01-01\"}",
                B_BRISBANE).andExpect(status().isNotFound());

        // Links to another business's rows are ignored rather than created.
        owner.post("/api/v1/admin/staff", """
                        {"displayName": "Sneaky", "locationIds": ["%s"]}""".formatted(B_BRISBANE))
                .andExpect(jsonPath("$[0].locationIds", empty()));
        org.assertj.core.api.Assertions.assertThat(
                admin.queryForObject("SELECT count(*) FROM staff_location", Integer.class)).isZero();
    }
}
