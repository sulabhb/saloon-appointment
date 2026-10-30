package com.salonbooking.identity.app;

import com.salonbooking.identity.data.AppUserRepository;
import com.salonbooking.identity.data.AuthSessionRepository;
import com.salonbooking.identity.data.MembershipQueries;
import com.salonbooking.identity.data.MembershipQueries.Access;
import com.salonbooking.identity.data.MembershipQueries.BusinessMembership;
import com.salonbooking.identity.domain.AppUser;
import com.salonbooking.identity.domain.AuthSession;
import com.salonbooking.identity.domain.SessionClient;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Staff sign-in and sessions.
 *
 * <p>Not transactional as a whole on purpose: some steps read memberships across businesses in
 * system scope, which must be bound before their own transaction starts (see TenantContext).
 * Each database step runs in its own short transaction.
 */
@Service
public class AuthService {

    public record SignIn(String token, AuthSession session, Me me) {}

    public record Me(UserView user, List<BusinessMembership> businesses, SelectedBusiness selected) {}

    public record UserView(UUID id, String email, String fullName, boolean platformAdmin) {}

    /** {@code locationIds} is null when the person sees every branch. */
    public record SelectedBusiness(UUID businessId, StaffRole role, Set<UUID> locationIds, UUID staffId) {}

    public record SessionView(UUID id, SessionClient client, String deviceName, Instant createdAt,
            Instant lastSeenAt, boolean current) {}

    private static final ApiException INVALID_CREDENTIALS =
            new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");

    private final AppUserRepository users;
    private final AuthSessionRepository sessions;
    private final MembershipQueries memberships;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    /** Checked against when the user is unknown, so response time does not reveal which emails exist. */
    private final String dummyPasswordHash;

    AuthService(AppUserRepository users, AuthSessionRepository sessions, MembershipQueries memberships,
            PasswordEncoder passwordEncoder, AuthProperties properties, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.sessions = sessions;
        this.memberships = memberships;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.dummyPasswordHash = passwordEncoder.encode(SessionTokens.newToken());
    }

    /**
     * Checks the password and opens a session. If the person belongs to exactly one business it is
     * selected straight away; otherwise they choose with {@link #selectBusiness}.
     */
    public SignIn signIn(String email, String password, SessionClient client, String deviceName) {
        return signIn(email, password, client, deviceName, null);
    }

    /** As {@link #signIn(String, String, SessionClient, String)}, selecting {@code preferredBusinessId} if the person belongs to it. */
    public SignIn signIn(String email, String password, SessionClient client, String deviceName, UUID preferredBusinessId) {
        AppUser user = requireValidCredentials(email, password);

        List<BusinessMembership> businesses = TenantContext.callAsSystem(() -> memberships.activeMemberships(user.getId()));
        if (businesses.isEmpty() && !user.isPlatformAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_BUSINESS_ACCESS",
                    "This account does not have access to any business.");
        }
        UUID selected = businesses.stream().map(BusinessMembership::businessId)
                .filter(id -> id.equals(preferredBusinessId))
                .findFirst()
                .orElse(businesses.size() == 1 ? businesses.getFirst().businessId() : null);

        String token = SessionTokens.newToken();
        Instant now = clock.instant();
        AuthSession session = sessions.save(new AuthSession(user.getId(), selected, SessionTokens.hash(token), client,
                deviceName, now, idleTimeout(client), absoluteTimeout(client)));

        SelectedBusiness selectedBusiness = selected == null ? null : selectedBusiness(user.getId(), selected).orElse(null);
        return new SignIn(token, session, new Me(view(user), businesses, selectedBusiness));
    }

    /** Resolves a session token to the signed-in person, or empty if the token is not usable. */
    public Optional<StaffPrincipal> authenticate(String token) {
        Instant now = clock.instant();
        Optional<AuthSession> found = sessions.findByTokenHash(SessionTokens.hash(token)).filter(s -> s.isValidAt(now));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthSession session = found.get();
        Optional<AppUser> user = users.findById(session.getUserId()).filter(AppUser::isActive);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        if (session.touch(now, idleTimeout(session.getClient()))) {
            sessions.save(session);
        }

        UUID businessId = session.getSelectedBusinessId();
        if (businessId == null) {
            return Optional.of(principal(user.get(), session, null));
        }
        // Access removed or business closed since sign-in: the session no longer works (STF-9).
        return selectedBusiness(user.get().getId(), businessId).map(selected -> principal(user.get(), session, selected));
    }

    public Me me(StaffPrincipal principal) {
        AppUser user = users.findById(principal.userId()).orElseThrow();
        List<BusinessMembership> businesses = TenantContext.callAsSystem(() -> memberships.activeMemberships(user.getId()));
        SelectedBusiness selected = principal.hasBusiness()
                ? new SelectedBusiness(principal.businessId(), principal.role(), principal.locationIds(), principal.staffId())
                : null;
        return new Me(view(user), businesses, selected);
    }

    /** Switches the session to another business the person belongs to. */
    public Me selectBusiness(StaffPrincipal principal, UUID businessId) {
        SelectedBusiness selected = selectedBusiness(principal.userId(), businessId)
                .orElseThrow(() -> new NotFoundException("Business not found."));
        transaction.executeWithoutResult(status -> sessions.findById(principal.sessionId())
                .orElseThrow()
                .selectBusiness(businessId));
        AppUser user = users.findById(principal.userId()).orElseThrow();
        List<BusinessMembership> businesses = TenantContext.callAsSystem(() -> memberships.activeMemberships(user.getId()));
        return new Me(view(user), businesses, selected);
    }

    public void signOut(StaffPrincipal principal) {
        revoke(principal.userId(), principal.sessionId());
    }

    public List<SessionView> activeSessions(StaffPrincipal principal) {
        return sessions.findActiveByUserId(principal.userId(), clock.instant()).stream()
                .map(s -> new SessionView(s.getId(), s.getClient(), s.getDeviceName(), s.getCreatedAt(),
                        s.getLastSeenAt(), s.getId().equals(principal.sessionId())))
                .toList();
    }

    /** Signs out one of the person's own sessions, for example a lost phone. */
    public void revokeSession(StaffPrincipal principal, UUID sessionId) {
        revoke(principal.userId(), sessionId);
    }

    public Duration absoluteTimeout(SessionClient client) {
        return client == SessionClient.WEB ? properties.webAbsoluteTimeout() : properties.androidAbsoluteTimeout();
    }

    private Duration idleTimeout(SessionClient client) {
        return client == SessionClient.WEB ? properties.webIdleTimeout() : properties.androidIdleTimeout();
    }

    private void revoke(UUID userId, UUID sessionId) {
        Instant now = clock.instant();
        transaction.executeWithoutResult(status -> sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found."))
                .revoke(now));
    }

    /**
     * Returns the account if the password is right, otherwise throws INVALID_CREDENTIALS. Failed
     * attempts count towards the lockout.
     */
    public AppUser requireValidCredentials(String email, String password) {
        return verifyCredentials(email.trim(), password).orElseThrow(() -> INVALID_CREDENTIALS);
    }

    public String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }

    /** Returns the user if the password is right. Failed attempts are counted and committed either way. */
    private Optional<AppUser> verifyCredentials(String email, String password) {
        Instant now = clock.instant();
        return transaction.execute(status -> {
            Optional<AppUser> found = users.findByEmail(email);
            if (found.isEmpty() || !found.get().canSignIn(now)) {
                passwordEncoder.matches(password, dummyPasswordHash);
                return Optional.empty();
            }
            AppUser user = found.get();
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                user.recordFailedLogin(now, properties.maxFailedLogins(), properties.lockoutDuration());
                return Optional.empty();
            }
            user.recordSuccessfulLogin();
            return Optional.of(user);
        });
    }

    private Optional<SelectedBusiness> selectedBusiness(UUID userId, UUID businessId) {
        Optional<Access> access = TenantContext.callForBusiness(businessId, () -> memberships.accessInCurrentBusiness(userId));
        return access.map(a -> new SelectedBusiness(businessId, a.role(), a.locationIds(), a.staffId()));
    }

    private static StaffPrincipal principal(AppUser user, AuthSession session, SelectedBusiness selected) {
        return new StaffPrincipal(user.getId(), user.getEmail(), session.getId(), session.getClient(),
                user.isPlatformAdmin(),
                selected == null ? null : selected.businessId(),
                selected == null ? null : selected.role(),
                selected == null ? null : selected.locationIds(),
                selected == null ? null : selected.staffId());
    }

    private static UserView view(AppUser user) {
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.isPlatformAdmin());
    }
}
