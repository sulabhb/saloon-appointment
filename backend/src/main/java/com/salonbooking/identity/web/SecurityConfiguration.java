package com.salonbooking.identity.web;

import com.salonbooking.identity.app.AuthProperties;
import com.salonbooking.identity.app.AuthService;
import com.salonbooking.shared.web.ApiErrorResponses;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        // bcrypt today; the stored "{id}" prefix lets the algorithm change without a reset.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AuthService authService, AuthProperties properties) throws Exception {
        http
                // Cookie sessions need CSRF tokens (SPA style: XSRF-TOKEN cookie, X-XSRF-TOKEN header).
                // Bearer requests cannot be forged cross-site; public endpoints carry no session; sign-in
                // and invite acceptance create the session, and its cookie is SameSite=Lax.
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(
                        SessionAuthenticationFilter::hasBearerToken,
                        request -> request.getRequestURI().startsWith("/api/v1/public/"),
                        request -> "POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(request.getRequestURI()),
                        // Password reset carries no session; the confirm step needs the emailed token.
                        request -> "POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/auth/password-reset"),
                        request -> "POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/invites/")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> ApiErrorResponses.write(response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in to continue."))
                        .accessDeniedHandler((request, response, e) -> ApiErrorResponses.write(response,
                                HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this.")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/v1/public/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/password-reset",
                                "/api/v1/auth/password-reset/confirm").permitAll()
                        .requestMatchers("/api/v1/invites/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").authenticated()
                        .requestMatchers("/api/v1/staff/**").hasRole("STAFF")
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("OWNER", "MANAGER")
                        .requestMatchers("/api/v1/platform/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll())
                .addFilterBefore(new SessionAuthenticationFilter(authService, properties.cookieName()),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
