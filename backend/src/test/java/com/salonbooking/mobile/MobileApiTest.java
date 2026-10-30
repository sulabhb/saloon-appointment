package com.salonbooking.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.salonbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** App Links verification and the minimum app version (APP-2, APP-4). */
@TestPropertySource(properties = {
        "salon.android.cert-fingerprints=AB:CD:EF:01",
        "salon.android.min-version-code=5",
        "salon.android.latest-version-code=7"})
class MobileApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void theBookingHostVouchesForTheAppSoLinksOpenIt() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(jsonPath("$[0].relation", contains("delegate_permission/common.handle_all_urls")))
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("com.salonbooking.app"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints", contains("AB:CD:EF:01")));
    }

    @Test
    void theAppLearnsTheVersionRules() throws Exception {
        mvc.perform(get("/api/v1/public/app-config").header("X-Client-Version", "android/0.1.0+1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minAndroidVersionCode").value(5))
                .andExpect(jsonPath("$.latestAndroidVersionCode").value(7))
                .andExpect(jsonPath("$.androidStoreUrl").isString());
    }

    @Test
    void oldAppBuildsAreAskedToUpdateAndOthersPass() throws Exception {
        mvc.perform(get("/api/v1/public/b/glow-hair").header("X-Client-Version", "android/0.1.0+4"))
                .andExpect(status().isUpgradeRequired())
                .andExpect(jsonPath("$.error.code").value("APP_UPDATE_REQUIRED"));
        mvc.perform(get("/api/v1/public/b/glow-hair").header("X-Client-Version", "android/0.2.0+5")).andExpect(status().isOk());
        // The web and headers without a build number are not affected.
        mvc.perform(get("/api/v1/public/b/glow-hair")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/b/glow-hair").header("X-Client-Version", "android/0.1.0")).andExpect(status().isOk());
    }

    @Test
    void buildNumbersAreReadOnlyFromTheExpectedFormat() {
        assertThat(ClientVersionFilter.androidBuild("android/0.1.0+12")).hasValue(12);
        assertThat(ClientVersionFilter.androidBuild("android/0.1.0")).isEmpty();
        assertThat(ClientVersionFilter.androidBuild("ios/1.0+3")).isEmpty();
        assertThat(ClientVersionFilter.androidBuild(null)).isEmpty();
    }
}
