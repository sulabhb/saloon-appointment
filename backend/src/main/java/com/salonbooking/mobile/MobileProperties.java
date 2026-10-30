package com.salonbooking.mobile;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Android app ({@code salon.android.*}).
 *
 * @param packageName the app's application id, for App Links
 * @param certFingerprints SHA-256 fingerprints of the signing certificates ("AB:CD:..."); App
 *     Links verification only trusts apps signed with one of these
 * @param minVersionCode builds below this must update before they can be used (APP-4)
 * @param latestVersionCode the newest build in the store, so the app can suggest updating
 */
@ConfigurationProperties("salon.android")
public record MobileProperties(
        @DefaultValue("com.salonbooking.app") String packageName,
        @DefaultValue("") List<String> certFingerprints,
        @DefaultValue("1") int minVersionCode,
        @DefaultValue("1") int latestVersionCode,
        @DefaultValue("https://play.google.com/store/apps/details?id=com.salonbooking.app") String storeUrl) {}
