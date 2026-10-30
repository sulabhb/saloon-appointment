package com.salonbooking.identity.web;

import com.salonbooking.identity.app.AuthService;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Signs in requests that carry a session token (bearer header from Android, cookie from the web)
 * and binds the session's business in {@link TenantContext} for the rest of the request.
 *
 * <p>Public booking endpoints are skipped: their business comes from the URL, never from a staff
 * session. Registered only inside the security filter chain, not as a servlet filter.
 */
class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final AuthService authService;
    private final String cookieName;

    SessionAuthenticationFilter(AuthService authService, String cookieName) {
        this.authService = authService;
        this.cookieName = cookieName;
    }

    static boolean hasBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith(BEARER_PREFIX);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PATH_HELPER.getPathWithinApplication(request).startsWith("/api/v1/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<StaffPrincipal> principal = token(request).flatMap(authService::authenticate);
        if (principal.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        StaffPrincipal staff = principal.get();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(staff, null, authorities(staff)));
        SecurityContextHolder.setContext(context);

        if (!staff.hasBusiness()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.callForBusinessChecked(staff.businessId(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private Optional<String> token(HttpServletRequest request) {
        if (hasBearerToken(request)) {
            return Optional.of(request.getHeader(HttpHeaders.AUTHORIZATION).substring(BEARER_PREFIX.length()).trim())
                    .filter(t -> !t.isEmpty());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** ROLE_STAFF only once a business is selected, so staff endpoints need a business. */
    private static List<GrantedAuthority> authorities(StaffPrincipal staff) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (staff.hasBusiness()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + staff.role().name()));
        }
        if (staff.platformAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
        }
        return authorities;
    }
}
