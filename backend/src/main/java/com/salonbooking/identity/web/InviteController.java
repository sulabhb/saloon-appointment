package com.salonbooking.identity.web;

import com.salonbooking.identity.app.AuthService.Me;
import com.salonbooking.identity.app.AuthService.SignIn;
import com.salonbooking.identity.app.InviteService;
import com.salonbooking.identity.app.InviteService.InvitePreview;
import com.salonbooking.identity.domain.SessionClient;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints behind an invite link: see the invite, then accept it (TEN-2). */
@RestController
@RequestMapping("/api/v1/invites/{token}")
class InviteController {

    /** {@code fullName} is required only when no account exists for the invited email. */
    record AcceptRequest(
            @Size(max = 200) String fullName,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Pattern(regexp = "web|android") String client,
            @Size(max = 100) String deviceName) {}

    record AcceptResponse(String token, Me me) {}

    private final InviteService inviteService;
    private final SessionCookies sessionCookies;

    InviteController(InviteService inviteService, SessionCookies sessionCookies) {
        this.inviteService = inviteService;
        this.sessionCookies = sessionCookies;
    }

    @GetMapping
    InvitePreview preview(@PathVariable String token) {
        return inviteService.preview(token);
    }

    @PostMapping("/accept")
    AcceptResponse accept(@PathVariable String token, @Valid @RequestBody AcceptRequest request,
            HttpServletResponse response) {
        SessionClient client = SessionClient.parse(request.client());
        SignIn signIn = inviteService.accept(token, request.fullName(), request.password(), client, request.deviceName());
        if (client == SessionClient.WEB) {
            sessionCookies.set(response, signIn.token(), client);
            return new AcceptResponse(null, signIn.me());
        }
        return new AcceptResponse(signIn.token(), signIn.me());
    }
}
