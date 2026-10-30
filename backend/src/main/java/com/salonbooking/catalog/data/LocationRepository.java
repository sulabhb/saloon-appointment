package com.salonbooking.catalog.data;

import com.salonbooking.catalog.domain.Location;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Queries are scoped to the current business by the tenant filter; no business parameter needed. */
public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findAllByActiveTrueOrderByName();

    Optional<Location> findBySlugAndActiveTrue(String slug);
}
