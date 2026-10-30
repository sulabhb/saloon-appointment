package com.salonbooking.identity.data;

import com.salonbooking.identity.domain.Membership;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scoped to the bound business by the tenant filter. */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByUserId(UUID userId);
}
