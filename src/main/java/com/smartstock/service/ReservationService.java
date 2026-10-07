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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final CacheInvalidationService cacheInvalidationService;
    private final InventoryReservationLock inventoryReservationLock;
    private final OutboxService outboxService;

    public ReservationService(
            ReservationRepository reservationRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            Clock clock,
            CacheInvalidationService cacheInvalidationService,
            InventoryReservationLock inventoryReservationLock,
            OutboxService outboxService
    ) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
        this.cacheInvalidationService = cacheInvalidationService;
        this.inventoryReservationLock = inventoryReservationLock;
        this.outboxService = outboxService;
    }

    @Transactional
    public ReservationResponse create(CreateReservationRequest request, Long customerId) {
        requirePositiveQuantity(request.quantity());
        // Serialize same-product allocation attempts across app instances. PostgreSQL's
        // transaction and Inventory @Version remain the final correctness boundary.
        inventoryReservationLock.lockProduct(request.productId());

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
        cacheInvalidationService.enqueue("inventoryByProductId", request.productId());

        Instant now = Instant.now(clock);
        Reservation reservation = new Reservation();
        reservation.setProduct(product);
        reservation.setCustomerId(customerId);
        reservation.setQuantity(request.quantity());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setExpiresAt(now.plus(RESERVATION_LIFETIME));

        return toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Transactional
    public ReservationResponse findById(Long id, Long actorId, boolean admin) {
        Reservation reservation = getReservation(id);
        requireOwnerOrAdmin(reservation, actorId, admin);
        expireIfNecessary(reservation);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse confirm(Long id, Long actorId, boolean admin) {
        Reservation reservation = getReservation(id);
        requireOwnerOrAdmin(reservation, actorId, admin);
        if (expireIfNecessary(reservation)) {
            return toResponse(reservation);
        }
        requireActive(reservation, "confirm");
        reservation.setStatus(ReservationStatus.CONFIRMED);
        cacheInvalidationService.enqueue("inventoryByProductId", reservation.getProductId());
        Reservation saved = reservationRepository.saveAndFlush(reservation);
        recordReservationEvent("RESERVATION_CREATED", saved);
        return toResponse(saved);
    }

    @Transactional
    public ReservationResponse cancel(Long id, Long actorId, boolean admin) {
        Reservation reservation = getReservation(id);
        requireOwnerOrAdmin(reservation, actorId, admin);
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
        cacheInvalidationService.enqueue("inventoryByProductId", reservation.getProductId());

        reservation.setStatus(ReservationStatus.CANCELLED);
        return toResponse(reservationRepository.saveAndFlush(reservation));
    }

    @Transactional
    public int expireActiveReservations() {
        Instant now = Instant.now(clock);
        List<Reservation> expiredReservations = reservationRepository
                .findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        ReservationStatus.ACTIVE, now
                );
        if (expiredReservations.isEmpty()) {
            return 0;
        }
        List<Long> productIds = expiredReservations.stream()
                .map(Reservation::getProductId).distinct().toList();
        Map<Long, Inventory> inventories = inventoryRepository.findAllByProduct_IdIn(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        for (Reservation reservation : expiredReservations) {
            Inventory inventory = inventories.get(reservation.getProductId());
            if (inventory == null) {
                throw new ResourceNotFoundException(
                        "Inventory not found for product: " + reservation.getProductId()
                );
            }
            releaseInventory(inventory, reservation);
            reservation.setStatus(ReservationStatus.EXPIRED);
            recordReservationEvent("RESERVATION_EXPIRED", reservation);
        }
        for (Long productId : productIds) {
            cacheInvalidationService.enqueue("inventoryByProductId", productId);
        }
        inventoryRepository.saveAllAndFlush(inventories.values());
        reservationRepository.saveAllAndFlush(expiredReservations);
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
        releaseInventory(inventory, reservation);

        reservation.setStatus(ReservationStatus.EXPIRED);
        recordReservationEvent("RESERVATION_EXPIRED", reservation);
        cacheInvalidationService.enqueue("inventoryByProductId", reservation.getProductId());
        reservationRepository.saveAndFlush(reservation);
    }

    private void releaseInventory(Inventory inventory, Reservation reservation) {
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
    }

    private void recordReservationEvent(String eventType, Reservation reservation) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("reservationId", reservation.getId());
        payload.put("productId", reservation.getProductId());
        payload.put("customerId", reservation.getCustomerId());
        payload.put("quantity", reservation.getQuantity());
        payload.put("status", reservation.getStatus());
        payload.put("expiresAt", reservation.getExpiresAt());
        outboxService.record(eventType, "RESERVATION", reservation.getId(), payload);
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

    private void requireOwnerOrAdmin(Reservation reservation, Long actorId, boolean admin) {
        if (!admin && (reservation.getCustomerId() == null
                || !reservation.getCustomerId().equals(actorId))) {
            throw new ResourceNotFoundException("Reservation not found with id: " + reservation.getId());
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
