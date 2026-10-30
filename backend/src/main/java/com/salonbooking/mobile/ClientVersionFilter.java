package com.salonbooking.mobile;

import com.salonbooking.shared.web.ApiErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns away Android builds older than the minimum with 426 APP_UPDATE_REQUIRED (APP-4). The app
 * sends {@code X-Client-Version: android/<name>+<build>}; web clients and older headers without a
 * build number pass. The app-config endpoint itself always answers, so the app can explain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ClientVersionFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Client-Version";
    private static final Pattern ANDROID = Pattern.compile("^android/[^+\\s]+\\+(\\d{1,9})$");

    private final MobileProperties properties;

    ClientVersionFilter(MobileProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.equals("/api/v1/public/app-config");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        OptionalInt build = androidBuild(request.getHeader(HEADER));
        if (build.isPresent() && build.getAsInt() < properties.minVersionCode()) {
            ApiErrorResponses.write(response, HttpStatus.UPGRADE_REQUIRED, "APP_UPDATE_REQUIRED",
                    "Please update the app to keep booking.");
            return;
        }
        chain.doFilter(request, response);
    }

    static OptionalInt androidBuild(String header) {
        if (header == null) {
            return OptionalInt.empty();
        }
        Matcher m = ANDROID.matcher(header.trim());
        return m.matches() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
    }
}
