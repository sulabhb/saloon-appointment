package com.salonbooking.support;

import static com.salonbooking.support.StaffClient.bearer;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Calls the API as one signed-in person (bearer token) in tests. */
public final class AdminClient {

    private final MockMvc mvc;
    private final String token;

    public AdminClient(MockMvc mvc, String token) {
        this.mvc = mvc;
        this.token = token;
    }

    public ResultActions get(String path, Object... vars) throws Exception {
        return mvc.perform(bearer(MockMvcRequestBuilders.get(path, vars), token));
    }

    public ResultActions post(String path, String json, Object... vars) throws Exception {
        return mvc.perform(bearer(MockMvcRequestBuilders.post(path, vars), token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions put(String path, String json, Object... vars) throws Exception {
        return mvc.perform(bearer(MockMvcRequestBuilders.put(path, vars), token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions delete(String path, Object... vars) throws Exception {
        return mvc.perform(bearer(MockMvcRequestBuilders.delete(path, vars), token));
    }
}
