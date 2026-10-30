package com.salonbooking.identity.data;

import com.salonbooking.identity.domain.AppUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Case-insensitive through the citext column; the cast keeps PostgreSQL from comparing as text. */
    @Query(value = "SELECT * FROM app_user WHERE email = CAST(:email AS citext)", nativeQuery = true)
    Optional<AppUser> findByEmail(String email);
}
