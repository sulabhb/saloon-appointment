package com.salonbooking.tenancy;

import com.salonbooking.platform.app.BusinessDirectory;
import com.salonbooking.platform.app.BusinessDirectory.BusinessRef;
import com.salonbooking.shared.web.ApiErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Binds the business named in public booking URLs ({@code /api/v1/public/b/{businessSlug}/...})
 * for the rest of the request. Only Live businesses accept public traffic (TEN-3, TEN-4).
 */
@Component
class PublicBusinessTenantFilter extends OncePerRequestFilter {

    private static final Pattern PUBLIC_BUSINESS_PATH = Pattern.compile("^/api/v1/public/b/([^/]+)(?:/.*)?$");
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final BusinessDirectory businessDirectory;

    PublicBusinessTenantFilter(BusinessDirectory businessDirectory) {
        this.businessDirectory = businessDirectory;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PUBLIC_BUSINESS_PATH.matcher(PATH_HELPER.getPathWithinApplication(request)).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Matcher matcher = PUBLIC_BUSINESS_PATH.matcher(PATH_HELPER.getPathWithinApplication(request));
        if (!matcher.matches()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<BusinessRef> business = businessDirectory.findBySlug(matcher.group(1));
        switch (business.map(BusinessRef::status).orElse(null)) {
            case LIVE -> {
                try {
                    TenantContext.callForBusinessChecked(business.get().id(), () -> {
                        chain.doFilter(request, response);
                        return null;
                    });
                } catch (IOException | ServletException | RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ServletException(e);
                }
            }
            case SUSPENDED -> ApiErrorResponses.write(response, HttpStatus.SERVICE_UNAVAILABLE, "BUSINESS_UNAVAILABLE",
                    "Online booking for this business is currently unavailable.");
            case null, default -> ApiErrorResponses.write(response, HttpStatus.NOT_FOUND, "NOT_FOUND", "Business not found.");
        }
    }
}
