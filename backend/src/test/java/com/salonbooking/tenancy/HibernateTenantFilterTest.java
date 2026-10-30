package com.salonbooking.tenancy;

import static com.salonbooking.support.TestData.ALL_LOCATION_COUNT;
import static com.salonbooking.support.TestData.A_LOCATION_COUNT;
import static com.salonbooking.support.TestData.BUSINESS_A;
import static com.salonbooking.support.TestData.B_BRISBANE;
import static org.assertj.core.api.Assertions.assertThat;

import com.salonbooking.catalog.data.LocationRepository;
import com.salonbooking.catalog.domain.Location;
import com.salonbooking.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Application layer of tenant isolation: repositories without any business parameter. */
class HibernateTenantFilterTest extends IntegrationTest {

    @Autowired
    LocationRepository locations;

    @Test
    void findAllReturnsOnlyTheBoundBusiness() {
        List<Location> found = TenantContext.callForBusiness(BUSINESS_A, locations::findAll);

        assertThat(found).hasSize(A_LOCATION_COUNT).allSatisfy(l -> assertThat(l.getBusinessId()).isEqualTo(BUSINESS_A));
    }

    @Test
    void anotherBusinessesRowIsNotFoundById() {
        assertThat(TenantContext.callForBusiness(BUSINESS_A, () -> locations.findById(B_BRISBANE))).isEmpty();
    }

    @Test
    void nothingIsFoundWithoutABusiness() {
        assertThat(locations.findAll()).isEmpty();
    }

    @Test
    void newRowsGetTheBoundBusiness() {
        UUID id = TenantContext.callForBusiness(BUSINESS_A, () -> locations.save(
                new Location("Bondi", "bondi", "2 Beach Road", "Bondi", "NSW", "2026", "Australia/Sydney")).getId());

        UUID storedBusiness = admin.queryForObject("SELECT business_id FROM location WHERE id = ?", UUID.class, id);
        assertThat(storedBusiness).isEqualTo(BUSINESS_A);
    }

    @Test
    void systemScopeSeesEveryBusiness() {
        List<Location> found = TenantContext.callAsSystem(locations::findAll);

        assertThat(found).hasSize(ALL_LOCATION_COUNT);
    }
}
