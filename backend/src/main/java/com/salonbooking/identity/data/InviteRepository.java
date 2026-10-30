package com.salonbooking.identity.data;

import com.salonbooking.identity.domain.Invite;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Scoped to the bound business by the tenant filter, except token lookups in system scope. */
public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByTokenHash(byte[] tokenHash);

    /** Locks the invite so two people cannot accept it at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invite i WHERE i.id = :id")
    Optional<Invite> findForUpdate(UUID id);

    /** Native SQL skips Hibernate's tenant filter, so the business is passed explicitly (RLS checks it again). */
    @Query(value = """
            SELECT * FROM invite
            WHERE business_id = :businessId AND email = CAST(:email AS citext)
              AND accepted_at IS NULL AND revoked_at IS NULL""",
            nativeQuery = true)
    List<Invite> findOpenByEmail(UUID businessId, String email);

    List<Invite> findAllByOrderByCreatedAtDesc();
}
