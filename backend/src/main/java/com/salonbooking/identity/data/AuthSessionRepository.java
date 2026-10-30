package com.salonbooking.identity.data;

import com.salonbooking.identity.domain.AuthSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByTokenHash(byte[] tokenHash);

    Optional<AuthSession> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT s FROM AuthSession s
            WHERE s.userId = :userId AND s.revokedAt IS NULL
              AND s.idleExpiresAt > :now AND s.absoluteExpiresAt > :now
            ORDER BY s.lastSeenAt DESC""")
    List<AuthSession> findActiveByUserId(UUID userId, Instant now);
}
