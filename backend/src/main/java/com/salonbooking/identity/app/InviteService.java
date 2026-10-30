package com.salonbooking.identity.app;

import com.salonbooking.identity.app.AuthService.SignIn;
import com.salonbooking.identity.data.AppUserRepository;
import com.salonbooking.identity.data.InviteRepository;
import com.salonbooking.identity.data.MembershipRepository;
import com.salonbooking.identity.domain.AppUser;
import com.salonbooking.identity.domain.Invite;
import com.salonbooking.identity.domain.Membership;
import com.salonbooking.identity.domain.SessionClient;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.platform.app.BusinessDirectory;
import com.salonbooking.platform.domain.BusinessStatus;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invitations to join a business (TEN-2). Invite links carry a random token; only its hash is stored.
 * Until email delivery exists, the link is returned to whoever created the invite to pass on.
 */
@Service
public class InviteService {

    /** {@code url} is the only copy of the token; it cannot be shown again later. */
    public record IssuedInvite(UUID id, String email, StaffRole role, String url, Instant expiresAt) {}

    public record InviteView(UUID id, String email, StaffRole role, Invite.Status status, Instant expiresAt,
            Instant createdAt) {}

    /** What the accept page shows before the person commits. */
    public record InvitePreview(String businessName, String email, StaffRole role, boolean accountExists,
            Instant expiresAt) {}

    /** Length over complexity rules (NIST SP 800-63B). */
    static final int MIN_PASSWORD_LENGTH = 12;

    private static final ApiException INVITE_EXPIRED = new ApiException(HttpStatus.GONE, "INVITE_EXPIRED",
            "This invite has expired or was already used. Ask for a new one.");

    private final InviteRepository invites;
    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final AuthService authService;
    private final BusinessDirectory businessDirectory;
    private final AuthProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final JdbcClient jdbc;

    InviteService(InviteRepository invites, MembershipRepository memberships, AppUserRepository users,
            AuthService authService, BusinessDirectory businessDirectory, AuthProperties properties, Clock clock,
            PlatformTransactionManager transactionManager, JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.invites = invites;
        this.memberships = memberships;
        this.users = users;
        this.authService = authService;
        this.businessDirectory = businessDirectory;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Invites {@code email} to the bound business, replacing any open invite for the same address.
     * Joins the caller's transaction so a new business and its owner invite are created together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedInvite issue(String email, StaffRole role, UUID invitedByUserId) {
        return issue(email, role, invitedByUserId, null, List.of());
    }

    /**
     * As {@link #issue(String, StaffRole, UUID)}, also linking the person to a staff profile and
     * giving them access to {@code locationIds} when they accept (owners see every branch anyway).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedInvite issue(String email, StaffRole role, UUID invitedByUserId, UUID staffId, List<UUID> locationIds) {
        Instant now = clock.instant();
        UUID businessId = TenantContext.requireBusinessId();
        String normalizedEmail = email.trim();
        invites.findOpenByEmail(businessId, normalizedEmail).forEach(open -> open.revoke(now));

        String token = SessionTokens.newToken();
        Invite invite = invites.saveAndFlush(new Invite(normalizedEmail, role, SessionTokens.hash(token), invitedByUserId,
                staffId, now.plus(properties.inviteValidity())));
        if (!role.seesAllBranches()) {
            for (UUID locationId : locationIds.stream().distinct().toList()) {
                jdbc.sql("""
                                INSERT INTO invite_location (business_id, invite_id, location_id)
                                SELECT :businessId, :inviteId, id FROM location WHERE business_id = :businessId AND id = :locationId""")
                        .param("businessId", businessId).param("inviteId", invite.getId()).param("locationId", locationId)
                        .update();
            }
        }
        return new IssuedInvite(invite.getId(), normalizedEmail, role, url(token), invite.getExpiresAt());
    }

    /** Invites of the bound business, newest first. */
    @Transactional(readOnly = true)
    public List<InviteView> list() {
        Instant now = clock.instant();
        return invites.findAllByOrderByCreatedAtDesc().stream()
                .map(i -> new InviteView(i.getId(), i.getEmail(), i.getRole(), i.statusAt(now), i.getExpiresAt(),
                        i.getCreatedAt()))
                .toList();
    }

    /** Revokes an invite of the bound business. */
    @Transactional
    public void revoke(UUID inviteId) {
        invites.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Invite not found."))
                .revoke(clock.instant());
    }

    public InvitePreview preview(String token) {
        Invite invite = pendingInvite(token);
        String businessName = businessDirectory.getProfile(invite.getBusinessId()).displayName();
        boolean accountExists = users.findByEmail(invite.getEmail()).isPresent();
        return new InvitePreview(businessName, invite.getEmail(), invite.getRole(), accountExists, invite.getExpiresAt());
    }

    /**
     * Accepts an invite and signs the person in to the business.
     *
     * <p>New people choose their name and password here; the invite link proves they own the email.
     * People who already have an account confirm with their existing password instead.
     */
    public SignIn accept(String token, String fullName, String password, SessionClient client, String deviceName) {
        Invite pending = pendingInvite(token);
        UUID businessId = pending.getBusinessId();
        String email = pending.getEmail();

        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            authService.requireValidCredentials(email, password);
        } else if (fullName == null || fullName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Enter your full name.");
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT",
                    "Choose a password of at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        String newPasswordHash = existing.isPresent() ? null : authService.hashPassword(password);

        TenantContext.runForBusiness(businessId, () -> transaction.executeWithoutResult(status -> {
            Instant now = clock.instant();
            Invite invite = invites.findForUpdate(pending.getId()).orElseThrow(() -> INVITE_EXPIRED);
            if (invite.statusAt(now) != Invite.Status.PENDING) {
                throw INVITE_EXPIRED;
            }
            AppUser user = existing.orElseGet(() -> users.save(new AppUser(email, fullName.trim(), newPasswordHash)));
            Membership membership = memberships.findByUserId(user.getId())
                    .map(m -> {
                        m.reactivate(invite.getRole(), invite.getStaffId());
                        return m;
                    })
                    .orElseGet(() -> new Membership(user.getId(), invite.getRole(), invite.getStaffId()));
            membership = memberships.saveAndFlush(membership);
            // Branch access comes from the invite; owners see all branches and have no rows.
            jdbc.sql("DELETE FROM membership_location WHERE business_id = :businessId AND membership_id = :membershipId")
                    .param("businessId", businessId).param("membershipId", membership.getId()).update();
            jdbc.sql("""
                            INSERT INTO membership_location (business_id, membership_id, location_id)
                            SELECT business_id, :membershipId, location_id FROM invite_location
                            WHERE business_id = :businessId AND invite_id = :inviteId""")
                    .param("businessId", businessId).param("membershipId", membership.getId())
                    .param("inviteId", invite.getId())
                    .update();
            invite.accept(user.getId(), now);
        }));

        return authService.signIn(email, password, client, deviceName, businessId);
    }

    /** Finds a usable invite by token. Runs in system scope: the business is not known until the invite is found. */
    private Invite pendingInvite(String token) {
        Invite invite = TenantContext.callAsSystem(() -> invites.findByTokenHash(SessionTokens.hash(token)))
                .orElseThrow(() -> new NotFoundException("Invite not found."));
        if (invite.statusAt(clock.instant()) != Invite.Status.PENDING
                || businessDirectory.findStatus(invite.getBusinessId()).orElse(BusinessStatus.CLOSED) == BusinessStatus.CLOSED) {
            throw INVITE_EXPIRED;
        }
        return invite;
    }

    private String url(String token) {
        return properties.appBaseUrl().replaceAll("/+$", "") + "/invite/" + token;
    }
}
