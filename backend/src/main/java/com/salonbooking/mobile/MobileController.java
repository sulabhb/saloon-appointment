package com.salonbooking.mobile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the Android app needs from the booking host: App Links verification and its version rules. */
@RestController
@EnableConfigurationProperties(MobileProperties.class)
class MobileController {

    record AppConfig(int minAndroidVersionCode, int latestAndroidVersionCode, String androidStoreUrl) {}

    private final MobileProperties properties;

    MobileController(MobileProperties properties) {
        this.properties = properties;
    }

    /**
     * Digital Asset Links for the booking host, so booking and management links open the app when
     * it is installed (APP-2, MGT-1). Served at the host root, as Android requires.
     */
    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<List<Map<String, Object>>> assetLinks() {
        List<String> fingerprints = properties.certFingerprints().stream().filter(f -> !f.isBlank()).toList();
        List<Map<String, Object>> statements = fingerprints.isEmpty() ? List.of() : List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", Map.of(
                        "namespace", "android_app",
                        "package_name", properties.packageName(),
                        "sha256_cert_fingerprints", fingerprints)));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1))).body(statements);
    }

    /** Checked by the app at launch; unauthenticated and the same for every business (APP-4). */
    @GetMapping("/api/v1/public/app-config")
    AppConfig appConfig() {
        return new AppConfig(properties.minVersionCode(), Math.max(properties.latestVersionCode(), properties.minVersionCode()),
                properties.storeUrl());
    }
}
