package com.salonbooking.platform.app;

import com.salonbooking.platform.data.BusinessRepository;
import com.salonbooking.platform.domain.Business;
import com.salonbooking.platform.domain.BusinessStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to businesses for other modules. */
@Service
@Transactional(readOnly = true)
public class BusinessDirectory {

    public record BusinessRef(UUID id, String slug, BusinessStatus status) {}

    public record BusinessProfile(UUID id, String slug, String displayName, String description, String accentColor,
            int cancelCutoffMin) {}

    private final BusinessRepository businesses;

    BusinessDirectory(BusinessRepository businesses) {
        this.businesses = businesses;
    }

    public Optional<BusinessRef> findBySlug(String slug) {
        return businesses.findBySlug(slug).map(b -> new BusinessRef(b.getId(), b.getSlug(), b.getStatus()));
    }

    public Optional<BusinessStatus> findStatus(UUID businessId) {
        return businesses.findById(businessId).map(Business::getStatus);
    }

    public BusinessProfile getProfile(UUID businessId) {
        Business b = businesses.findById(businessId)
                .orElseThrow(() -> new IllegalStateException("Business " + businessId + " does not exist"));
        return new BusinessProfile(b.getId(), b.getSlug(), b.getDisplayName(), b.getDescription(), b.getAccentColor(),
                b.getCancelCutoffMin());
    }
}
