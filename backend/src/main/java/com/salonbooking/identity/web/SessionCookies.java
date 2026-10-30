package com.salonbooking.identity.web;

import com.salonbooking.identity.app.AuthProperties;
import com.salonbooking.identity.app.AuthService;
import com.salonbooking.identity.domain.SessionClient;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** The web session cookie: HTTP-only, Secure (configurable for local HTTP), SameSite=Lax. */
@Component
class SessionCookies {

    private final AuthProperties properties;
    private final AuthService authService;

    SessionCookies(AuthProperties properties, AuthService authService) {
        this.properties = properties;
        this.authService = authService;
    }

    void set(HttpServletResponse response, String token, SessionClient client) {
        write(response, token, authService.absoluteTimeout(client));
    }

    void clear(HttpServletResponse response) {
        write(response, "", Duration.ZERO);
    }

    private void write(HttpServletResponse response, String token, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), token)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
