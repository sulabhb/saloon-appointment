package com.salonbooking.identity.app;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Staff sign-in settings ({@code salon.auth.*}). Defaults follow requirements DSH-1 and NFR-SEC-4. */
@ConfigurationProperties("salon.auth")
public record AuthProperties(
        @DefaultValue("12h") Duration webIdleTimeout,
        @DefaultValue("7d") Duration webAbsoluteTimeout,
        @DefaultValue("30d") Duration androidIdleTimeout,
        @DefaultValue("90d") Duration androidAbsoluteTimeout,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockoutDuration,
        @DefaultValue("SALON_SESSION") String cookieName,
        @DefaultValue("true") boolean cookieSecure,
        @DefaultValue("7d") Duration inviteValidity,
        /** Where invite links point: the web console, which shows the accept page. */
        @DefaultValue("http://localhost:8080") String appBaseUrl) {}
