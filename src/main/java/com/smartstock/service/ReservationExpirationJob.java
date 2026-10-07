package com.smartstock.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationJob {

    private final ReservationService reservationService;

    public ReservationExpirationJob(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelayString = "${reservation.expiration-scan-interval-ms:30000}")
    public void expireReservations() {
        reservationService.expireActiveReservations();
    }
}
