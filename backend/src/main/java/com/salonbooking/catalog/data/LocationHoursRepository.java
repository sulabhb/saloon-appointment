package com.salonbooking.catalog.data;

import com.salonbooking.catalog.domain.LocationHours;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationHoursRepository extends JpaRepository<LocationHours, UUID> {

    List<LocationHours> findAllByLocationIdOrderByWeekdayAscOpensAtAsc(UUID locationId);
}
