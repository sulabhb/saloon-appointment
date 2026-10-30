package com.salonbooking.platform.web;

/** Request bodies shared by platform and invite tests. */
public final class PlatformBusinessApiTestData {

    private PlatformBusinessApiTestData() {}

    public static String newBusiness(String ownerEmail) {
        return PlatformBusinessApiTest.NEW_BUSINESS.replace("maya@bloomstudio.test", ownerEmail);
    }
}
