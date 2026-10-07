package com.smartstock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartstock.dto.CreateReservationRequest;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    private ReservationService reservationService;
    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                reservationRepository,
                productRepository,
                inventoryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);

        inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(10);
        inventory.setReservedQuantity(2);
        ReflectionTestUtils.setField(inventory, "id", 5L);
        ReflectionTestUtils.setField(inventory, "version", 0L);
    }

    @Test
    void createMovesStockAndSetsTenMinuteExpiry() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProduct_Id(1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);
        when(reservationRepository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> {
                    Reservation reservation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(reservation, "id", 9L);
                    return reservation;
                });

        var response = reservationService.create(new CreateReservationRequest(1L, 3L));

        assertThat(inventory.getAvailableQuantity()).isEqualTo(7);
        assertThat(inventory.getReservedQuantity()).isEqualTo(5);
        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        verify(inventoryRepository).saveAndFlush(inventory);
        verify(reservationRepository).saveAndFlush(any(Reservation.class));
    }

    @Test
    void createRequiresExistingProductAndInventory() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reservationService.create(new CreateReservationRequest(99L, 1L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProduct_Id(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                reservationService.create(new CreateReservationRequest(1L, 1L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Inventory not found");
    }

    @Test
    void createRejectsInsufficientStockWithoutChangingInventory() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProduct_Id(1L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() ->
                reservationService.create(new CreateReservationRequest(1L, 11L)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested reservation 11");

        verify(inventoryRepository, never()).saveAndFlush(any(Inventory.class));
        verify(reservationRepository, never()).saveAndFlush(any(Reservation.class));
    }

    @Test
    void confirmChangesOnlyActiveReservationStatus() {
        Reservation reservation = reservation(ReservationStatus.ACTIVE, NOW.plusSeconds(60));
        when(reservationRepository.findById(4L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.saveAndFlush(reservation)).thenReturn(reservation);

        var response = reservationService.confirm(4L);

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(inventoryRepository, never()).saveAndFlush(any(Inventory.class));
    }

    @Test
    void cannotConfirmCancelledReservation() {
        Reservation reservation = reservation(ReservationStatus.CANCELLED, NOW.plusSeconds(60));
        when(reservationRepository.findById(4L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirm(4L))
                .isInstanceOf(ReservationStateException.class)
                .hasMessageContaining("status is CANCELLED");
    }

    @Test
    void cannotCancelConfirmedReservation() {
        Reservation reservation = reservation(ReservationStatus.CONFIRMED, NOW.plusSeconds(60));
        when(reservationRepository.findById(4L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.cancel(4L))
                .isInstanceOf(ReservationStateException.class)
                .hasMessageContaining("status is CONFIRMED");

        verify(inventoryRepository, never()).saveAndFlush(any(Inventory.class));
    }

    @Test
    void expiredReservationCannotBeConfirmed() {
        Reservation reservation = reservation(ReservationStatus.EXPIRED, NOW.plusSeconds(60));
        when(reservationRepository.findById(4L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.confirm(4L))
                .isInstanceOf(ReservationStateException.class)
                .hasMessageContaining("status is EXPIRED");
    }

    @Test
    void cancelReturnsQuantityToAvailableStock() {
        Reservation reservation = reservation(ReservationStatus.ACTIVE, NOW.plusSeconds(60));
        when(reservationRepository.findById(4L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProduct_Id(1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);
        when(reservationRepository.saveAndFlush(reservation)).thenReturn(reservation);

        var response = reservationService.cancel(4L);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(12);
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    @Test
    void expiredReservationReleasesStockAndCannotBeConfirmed() {
        Reservation reservation = reservation(ReservationStatus.ACTIVE, NOW.minusSeconds(1));
        when(reservationRepository.findById(4L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProduct_Id(1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);
        when(reservationRepository.saveAndFlush(reservation)).thenReturn(reservation);

        var response = reservationService.confirm(4L);

        assertThat(response.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(12);
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    @Test
    void scheduledExpirationFindsAndExpiresDueActiveReservations() {
        Reservation reservation = reservation(ReservationStatus.ACTIVE, NOW);
        when(reservationRepository.findAllByStatusAndExpiresAtLessThanEqual(
                ReservationStatus.ACTIVE,
                NOW
        )).thenReturn(List.of(reservation));
        when(inventoryRepository.findByProduct_Id(1L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);
        when(reservationRepository.saveAndFlush(reservation)).thenReturn(reservation);

        int expired = reservationService.expireActiveReservations();

        assertThat(expired).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(12);
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    private Reservation reservation(ReservationStatus status, Instant expiresAt) {
        Reservation reservation = new Reservation();
        reservation.setProduct(product);
        reservation.setQuantity(2);
        reservation.setStatus(status);
        reservation.setExpiresAt(expiresAt);
        ReflectionTestUtils.setField(reservation, "id", 4L);
        ReflectionTestUtils.setField(reservation, "version", 0L);
        return reservation;
    }
}
