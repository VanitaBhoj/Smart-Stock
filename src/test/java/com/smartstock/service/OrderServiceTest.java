package com.smartstock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartstock.dto.CreateOrderRequest;
import com.smartstock.dto.CreateReservationRequest;
import com.smartstock.dto.OrderItemRequest;
import com.smartstock.dto.ReservationResponse;
import com.smartstock.entity.Order;
import com.smartstock.entity.OrderItem;
import com.smartstock.entity.OrderStatus;
import com.smartstock.entity.Product;
import com.smartstock.entity.Reservation;
import com.smartstock.entity.ReservationStatus;
import com.smartstock.exception.OrderStateException;
import com.smartstock.repository.OrderItemRepository;
import com.smartstock.repository.OrderRepository;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationService reservationService;
    @Mock private OutboxService outboxService;

    @InjectMocks
    private OrderService orderService;

    private Product firstProduct;
    private Product secondProduct;

    @BeforeEach
    void setUp() {
        firstProduct = product(1L, "Pen", "PEN", "12.50");
        secondProduct = product(2L, "Notebook", "NOTE", "4.00");
    }

    @Test
    void createReservesItemsAndCalculatesTotalFromCurrentPrices() {
        when(productRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(firstProduct, secondProduct));
        when(reservationService.create(any(CreateReservationRequest.class), eq(900L)))
                .thenReturn(reservationResponse(41L, 1L, ReservationStatus.ACTIVE))
                .thenReturn(reservationResponse(42L, 2L, ReservationStatus.ACTIVE));
        when(reservationRepository.getReferenceById(41L)).thenReturn(reservation(41L));
        when(reservationRepository.getReferenceById(42L)).thenReturn(reservation(42L));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 101L);
            }
            return order;
        });
        when(orderItemRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<OrderItem> items = invocation.getArgument(0);
            for (int index = 0; index < items.size(); index++) {
                ReflectionTestUtils.setField(items.get(index), "id", (long) index + 1);
            }
            return items;
        });

        var response = orderService.create(new CreateOrderRequest(
                List.of(new OrderItemRequest(1L, 2L), new OrderItemRequest(2L, 3L))
        ), 900L);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.totalAmount()).isEqualByComparingTo("37.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting("subtotal")
                .containsExactly(new BigDecimal("25.00"), new BigDecimal("12.00"));

        ArgumentCaptor<CreateReservationRequest> reservations =
                ArgumentCaptor.forClass(CreateReservationRequest.class);
        verify(reservationService, org.mockito.Mockito.times(2)).create(reservations.capture(), eq(900L));
        assertThat(reservations.getAllValues())
                .extracting(CreateReservationRequest::quantity)
                .containsExactly(2L, 3L);
    }

    @Test
    void confirmChangesOrderAndConfirmsItsReservation() {
        Order order = orderWithOneItem(OrderStatus.CREATED, 51L);
        when(orderRepository.findWithItemsById(101L)).thenReturn(Optional.of(order));
        when(reservationService.confirm(51L, 900L, false))
                .thenReturn(reservationResponse(51L, 1L, ReservationStatus.CONFIRMED));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        var response = orderService.confirm(101L, 900L, false);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(reservationService).confirm(51L, 900L, false);
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void cancelChangesOrderAndCancelsItsReservation() {
        Order order = orderWithOneItem(OrderStatus.CREATED, 52L);
        when(orderRepository.findWithItemsById(101L)).thenReturn(Optional.of(order));
        when(reservationService.cancel(52L, 900L, false))
                .thenReturn(reservationResponse(52L, 1L, ReservationStatus.CANCELLED));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        var response = orderService.cancel(101L, 900L, false);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(reservationService).cancel(52L, 900L, false);
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void confirmedOrderCannotBeCancelledOrConfirmedAgain() {
        Order order = orderWithOneItem(OrderStatus.CONFIRMED, 53L);
        when(orderRepository.findWithItemsById(101L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(101L, 900L, false))
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("status is CONFIRMED");
        assertThatThrownBy(() -> orderService.confirm(101L, 900L, false))
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("status is CONFIRMED");

        verify(reservationService, never()).cancel(53L);
        verify(reservationService, never()).confirm(53L);
    }

    private static Product product(Long id, String name, String sku, String price) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal(price));
        product.setActive(true);
        return product;
    }

    private static Reservation reservation(Long id) {
        Reservation reservation = new Reservation();
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private static ReservationResponse reservationResponse(
            Long id,
            Long productId,
            ReservationStatus status
    ) {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        return new ReservationResponse(
                id, productId, 1, status, now.plusSeconds(600), now, now
        );
    }

    private static Order orderWithOneItem(OrderStatus status, Long reservationId) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 101L);
        order.setCustomerId(900L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("12.50"));
        OrderItem item = new OrderItem();
        item.setProduct(product(1L, "Pen", "PEN", "12.50"));
        item.setReservation(reservation(reservationId));
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("12.50"));
        item.setSubtotal(new BigDecimal("12.50"));
        order.addItem(item);
        return order;
    }
}
