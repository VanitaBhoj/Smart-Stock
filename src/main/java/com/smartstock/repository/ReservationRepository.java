package com.smartstock.repository;

import com.smartstock.entity.Reservation;
import com.smartstock.entity.ReservationStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByStatusAndExpiresAtLessThanEqual(
            ReservationStatus status,
            Instant expiresAt
    );
}
