package com.salonbooking.platform.app;

import com.salonbooking.identity.app.InviteService;
import com.salonbooking.identity.app.InviteService.InviteView;
import com.salonbooking.identity.app.InviteService.IssuedInvite;
import com.salonbooking.identity.domain.StaffRole;
import com.salonbooking.platform.data.BusinessRepository;
import com.salonbooking.platform.data.PlatformBusinessQueries;
import com.salonbooking.platform.data.PlatformBusinessQueries.BusinessSummary;
import com.salonbooking.platform.data.PlatformBusinessQueries.NewBusiness;
import com.salonbooking.platform.domain.Business;
import com.salonbooking.platform.domain.BusinessStatus;
import com.salonbooking.shared.web.ApiException;
import com.salonbooking.shared.web.NotFoundException;
import com.salonbooking.tenancy.TenantContext;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Onboarding and managing businesses from the platform console (TEN-1 to TEN-4, TEN-10).
 *
 * <p>Platform admins see business records and counts, not business data such as customers.
 * Counts are read in system scope; invites are read in the scope of their business.
 */
@Service
public class PlatformBusinessService {

    public record CreateBusiness(String slug, String legalName, String displayName, String defaultTimeZone,
            String ownerEmail, String accentColor, String description) {}

    public record CreatedBusiness(BusinessSummary business, IssuedInvite ownerInvite) {}

    public record BusinessDetail(BusinessSummary business, List<InviteView> invites) {}

    /** Slugs that would clash with paths on the booking or console hosts. */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "api", "app", "admin", "book", "booking", "invite", "invites", "login", "platform", "static", "assets",
            "help", "support", "www", "mail", "staff", "manage", "public", "status");

    private final PlatformBusinessQueries queries;
    private final BusinessRepository businesses;
    private final InviteService inviteService;
    private final Clock clock;
    private final TransactionTemplate transaction;

    PlatformBusinessService(PlatformBusinessQueries queries, BusinessRepository businesses, InviteService inviteService,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.queries = queries;
        this.businesses = businesses;
        this.inviteService = inviteService;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Creates a business in Onboarding status and invites its owner, in one transaction. */
    public CreatedBusiness create(CreateBusiness request, UUID platformAdminUserId) {
        String slug = request.slug().trim().toLowerCase();
        if (RESERVED_SLUGS.contains(slug) || queries.slugExists(slug)) {
            throw slugTaken();
        }
        if (!ZoneId.getAvailableZoneIds().contains(request.defaultTimeZone())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Unknown time zone.");
        }

        UUID id = UUID.randomUUID();
        NewBusiness business = new NewBusiness(id, slug, request.legalName().trim(), request.displayName().trim(),
                request.defaultTimeZone(), request.accentColor(), request.description(), request.ownerEmail().trim());
        IssuedInvite invite;
        try {
            invite = TenantContext.callForBusiness(id, () -> transaction.execute(status -> {
                queries.insert(business);
                return inviteService.issue(request.ownerEmail(), StaffRole.OWNER, platformAdminUserId);
            }));
        } catch (DuplicateKeyException e) {
            throw slugTaken(); // Lost a race with another create using the same slug.
        }
        return new CreatedBusiness(summary(id), invite);
    }

    public List<BusinessSummary> list() {
        return TenantContext.callAsSystem(queries::summaries);
    }

    public BusinessDetail get(UUID businessId) {
        BusinessSummary summary = summary(businessId);
        List<InviteView> invites = TenantContext.callForBusiness(businessId, inviteService::list);
        return new BusinessDetail(summary, invites);
    }

    public BusinessSummary changeStatus(UUID businessId, BusinessStatus target) {
        transaction.executeWithoutResult(status -> {
            Business business = businesses.findById(businessId)
                    .orElseThrow(() -> new NotFoundException("Business not found."));
            if (!business.changeStatus(target, clock.instant())) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_CHANGE",
                        "A " + business.getStatus() + " business cannot become " + target + ".");
            }
        });
        return summary(businessId);
    }

    /** Sends a fresh owner invite, for example when the first one expired. Replaces open invites to the same email. */
    public IssuedInvite inviteOwner(UUID businessId, String email, UUID platformAdminUserId) {
        BusinessStatus status = businesses.findById(businessId).map(Business::getStatus)
                .orElseThrow(() -> new NotFoundException("Business not found."));
        if (status == BusinessStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "BUSINESS_CLOSED", "This business is closed.");
        }
        return TenantContext.callForBusiness(businessId, () -> transaction.execute(
                tx -> inviteService.issue(email, StaffRole.OWNER, platformAdminUserId)));
    }

    public void revokeInvite(UUID businessId, UUID inviteId) {
        TenantContext.runForBusiness(businessId, () -> inviteService.revoke(inviteId));
    }

    private BusinessSummary summary(UUID businessId) {
        return TenantContext.callAsSystem(() -> queries.summary(businessId))
                .orElseThrow(() -> new NotFoundException("Business not found."));
    }

    private static ApiException slugTaken() {
        return new ApiException(HttpStatus.CONFLICT, "SLUG_TAKEN", "That salon code is already in use.");
    }
}
