package com.smartstock.service;

import com.smartstock.dto.CreateReservationRequest;
import com.smartstock.dto.ReservationResponse;
import com.smartstock.entity.Inventory;
import com.smartstock.entity.Product;
import com.smartstock.entity.Reservation;
import com.smartstock.entity.ReservationStatus;
import com.smartstock.exception.InsufficientStockException;
import com.smartstock.exception.ReservationStateException;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.repository.InventoryRepository;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private static final Duration RESERVATION_LIFETIME = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    @Transactional
    @CacheEvict(cacheNames = "inventoryByProductId", key = "#request.productId")
    public ReservationResponse create(CreateReservationRequest request) {
        requirePositiveQuantity(request.quantity());

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + request.productId()
                ));
        Inventory inventory = inventoryRepository.findByProduct_Id(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product: " + request.productId()
                ));

        if (inventory.getAvailableQuantity() < request.quantity()) {
            throw new InsufficientStockException(
                    "Insufficient available stock for product " + request.productId()
                            + ": available " + inventory.getAvailableQuantity()
                            + ", requested reservation " + request.quantity()
            );
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.quantity());
        inventory.setReservedQuantity(Math.addExact(
                inventory.getReservedQuantity(),
                request.quantity()
        ));
        inventoryRepository.saveAndFlush(inventory);

        Instant now = Instant.now(clock);
        Reservation reservation = new Reservation();
        reservation.setProduct(product);
        reservation.setQuantity(request.quantity());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setExpiresAt(now.plus(RESERVATION_LIFETIME));

        return toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Transactional
    @CacheEvict(
            cacheNames = "inventoryByProductId",
            key = "#result.productId",
            condition = "#result.status.name() == 'EXPIRED'"
    )
    public ReservationResponse findById(Long id) {
        Reservation reservation = getReservation(id);
        expireIfNecessary(reservation);
        return toResponse(reservation);
    }

    @Transactional
    @CacheEvict(cacheNames = "inventoryByProductId", key = "#result.productId")
    public ReservationResponse confirm(Long id) {
        Reservation reservation = getReservation(id);
        if (expireIfNecessary(reservation)) {
            return toResponse(reservation);
        }
        requireActive(reservation, "confirm");
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Transactional
    @CacheEvict(cacheNames = "inventoryByProductId", key = "#result.productId")
    public ReservationResponse cancel(Long id) {
        Reservation reservation = getReservation(id);
        if (expireIfNecessary(reservation)) {
            return toResponse(reservation);
        }
        requireActive(reservation, "cancel");

        Inventory inventory = getInventory(reservation.getProductId());
        if (inventory.getReservedQuantity() < reservation.getQuantity()) {
            throw new ReservationStateException(
                    "Inventory reserved quantity is inconsistent for reservation " + id
            );
        }
        inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
        inventory.setAvailableQuantity(Math.addExact(
                inventory.getAvailableQuantity(),
                reservation.getQuantity()
        ));
        inventoryRepository.saveAndFlush(inventory);

        reservation.setStatus(ReservationStatus.CANCELLED);
        return toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Transactional
    @CacheEvict(
            cacheNames = "inventoryByProductId",
            allEntries = true,
            condition = "#result > 0"
    )
    public int expireActiveReservations() {
        Instant now = Instant.now(clock);
        List<Reservation> expiredReservations =
                reservationRepository.findAllByStatusAndExpiresAtLessThanEqual(
                        ReservationStatus.ACTIVE,
                        now
                );
        for (Reservation reservation : expiredReservations) {
            expire(reservation);
        }
        return expiredReservations.size();
    }

    private boolean expireIfNecessary(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.ACTIVE
                && !reservation.getExpiresAt().isAfter(Instant.now(clock))) {
            expire(reservation);
            return true;
        }
        return false;
    }

    private void expire(Reservation reservation) {
        Inventory inventory = getInventory(reservation.getProductId());
        if (inventory.getReservedQuantity() < reservation.getQuantity()) {
            throw new ReservationStateException(
                    "Inventory reserved quantity is inconsistent for reservation "
                            + reservation.getId()
            );
        }
        inventory.setReservedQuantity(inventory.getReservedQuantity() - reservation.getQuantity());
        inventory.setAvailableQuantity(Math.addExact(
                inventory.getAvailableQuantity(),
                reservation.getQuantity()
        ));
        inventoryRepository.saveAndFlush(inventory);

        reservation.setStatus(ReservationStatus.EXPIRED);
        reservationRepository.saveAndFlush(reservation);
    }

    private Reservation getReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with id: " + id
                ));
    }

    private Inventory getInventory(Long productId) {
        return inventoryRepository.findByProduct_Id(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product: " + productId
                ));
    }

    private void requireActive(Reservation reservation, String action) {
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ReservationStateException(
                    "Cannot " + action + " reservation " + reservation.getId()
                            + " because its status is " + reservation.getStatus()
            );
        }
    }

    private void requirePositiveQuantity(Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getProductId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
