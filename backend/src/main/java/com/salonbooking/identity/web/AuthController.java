package com.salonbooking.identity.web;

import com.salonbooking.identity.app.AuthService;
import com.salonbooking.identity.app.PasswordResetService;
import com.salonbooking.identity.app.AuthService.Me;
import com.salonbooking.identity.app.AuthService.SessionView;
import com.salonbooking.identity.app.AuthService.SignIn;
import com.salonbooking.identity.app.StaffPrincipal;
import com.salonbooking.identity.domain.SessionClient;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff sign-in for the web dashboard and the Android app (requirements DSH-1, APP-20).
 * Web sessions travel in an HTTP-only cookie; Android receives the token once in the response.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Pattern(regexp = "web|android") String client,
            @Size(max = 100) String deviceName) {}

    /** {@code token} is only set for Android; web clients get the cookie instead. */
    record LoginResponse(String token, Me me) {}

    record SelectBusinessRequest(@NotNull UUID businessId) {}

    record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    record NewPasswordRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 200) String password) {}

    record Accepted(String message) {}

    private final AuthService authService;
    private final SessionCookies sessionCookies;
    private final PasswordResetService passwordReset;

    AuthController(AuthService authService, SessionCookies sessionCookies, PasswordResetService passwordReset) {
        this.authService = authService;
        this.sessionCookies = sessionCookies;
        this.passwordReset = passwordReset;
    }

    /** The same answer whether or not the address has an account. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Accepted requestPasswordReset(@Valid @RequestBody ResetRequest request) {
        passwordReset.request(request.email());
        return new Accepted("If that email belongs to a staff account, we've sent a link to reset the password.");
    }

    /** Sets the new password and signs the person out of every device. */
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody NewPasswordRequest request) {
        passwordReset.reset(request.token(), request.password());
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        SessionClient client = SessionClient.parse(request.client());
        SignIn signIn = authService.signIn(request.email(), request.password(), client, request.deviceName());
        if (client == SessionClient.WEB) {
            sessionCookies.set(response, signIn.token(), client);
            return new LoginResponse(null, signIn.me());
        }
        return new LoginResponse(signIn.token(), signIn.me());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal StaffPrincipal principal, HttpServletResponse response) {
        authService.signOut(principal);
        if (principal.client() == SessionClient.WEB) {
            sessionCookies.clear(response);
        }
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal StaffPrincipal principal) {
        return authService.me(principal);
    }

    @PostMapping("/business")
    Me selectBusiness(@AuthenticationPrincipal StaffPrincipal principal, @Valid @RequestBody SelectBusinessRequest request) {
        return authService.selectBusiness(principal, request.businessId());
    }

    @GetMapping("/sessions")
    List<SessionView> sessions(@AuthenticationPrincipal StaffPrincipal principal) {
        return authService.activeSessions(principal);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(@AuthenticationPrincipal StaffPrincipal principal, @PathVariable UUID sessionId) {
        authService.revokeSession(principal, sessionId);
    }
}
