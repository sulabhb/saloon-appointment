package com.salonbooking.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Signs staff in through the real API and decorates requests with their session. */
public final class StaffClient {

    private final MockMvc mvc;

    public StaffClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public static String loginBody(String email, String password, String client, String deviceName) {
        return """
                {"email": "%s", "password": "%s", "client": "%s", "deviceName": "%s"}"""
                .formatted(email, password, client, deviceName);
    }

    /** Signs in as the Android app and returns the bearer token. */
    public String androidToken(String email) throws Exception {
        return androidToken(email, "Test phone");
    }

    public String androidToken(String email, String deviceName) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, TestData.PASSWORD, "android", deviceName)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    public static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
