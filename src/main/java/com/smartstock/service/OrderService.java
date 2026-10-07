package com.smartstock.service;

import com.smartstock.dto.CreateOrderRequest;
import com.smartstock.dto.CreateReservationRequest;
import com.smartstock.dto.OrderItemRequest;
import com.smartstock.dto.OrderItemResponse;
import com.smartstock.dto.OrderResponse;
import com.smartstock.dto.ReservationResponse;
import com.smartstock.entity.Order;
import com.smartstock.entity.OrderItem;
import com.smartstock.entity.OrderStatus;
import com.smartstock.entity.Product;
import com.smartstock.entity.ReservationStatus;
import com.smartstock.exception.OrderStateException;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.exception.ReservationStateException;
import com.smartstock.repository.OrderItemRepository;
import com.smartstock.repository.OrderRepository;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.ReservationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            ReservationRepository reservationRepository,
            ReservationService reservationService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        validateRequest(request);
        List<Long> productIds = request.items().stream()
                .map(OrderItemRequest::productId)
                .distinct()
                .toList();
        Map<Long, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : productIds) {
            Product product = products.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("Product not found with id: " + productId);
            }
            if (!product.isActive()) {
                throw new OrderStateException("Product is inactive: " + productId);
            }
        }

        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.ZERO.setScale(2));
        order = orderRepository.saveAndFlush(order);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.items()) {
            Product product = products.get(itemRequest.productId());
            ReservationResponse reservation = reservationService.create(
                    new CreateReservationRequest(itemRequest.productId(), itemRequest.quantity())
            );
            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setReservation(reservationRepository.getReferenceById(reservation.id()));
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(unitPrice);
            item.setSubtotal(subtotal);
            order.addItem(item);
            total = total.add(subtotal);
        }
        order.setTotalAmount(total);
        orderItemRepository.saveAllAndFlush(order.getItems());
        orderRepository.saveAndFlush(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse findById(Long id) {
        return toResponse(getOrder(id));
    }

    @Transactional
    public List<OrderResponse> findByCustomerId(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("customerId must be positive");
        }
        return orderRepository.findAllWithItemsByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse confirm(Long id) {
        Order order = getOrder(id);
        requireCreated(order, "confirm");
        for (OrderItem item : order.getItems()) {
            ReservationResponse reservation = reservationService.confirm(item.getReservationId());
            if (reservation.status() != ReservationStatus.CONFIRMED) {
                throw new ReservationStateException(
                        "Reservation " + reservation.id() + " could not be confirmed"
                );
            }
        }
        order.setStatus(OrderStatus.CONFIRMED);
        return toResponse(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        Order order = getOrder(id);
        requireCreated(order, "cancel");
        for (OrderItem item : order.getItems()) {
            ReservationResponse reservation = reservationService.cancel(item.getReservationId());
            if (reservation.status() != ReservationStatus.CANCELLED
                    && reservation.status() != ReservationStatus.EXPIRED) {
                throw new ReservationStateException(
                        "Reservation " + reservation.id() + " could not be cancelled"
                );
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(orderRepository.saveAndFlush(order));
    }

    private Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private void validateRequest(CreateOrderRequest request) {
        if (request == null || request.customerId() == null || request.customerId() <= 0) {
            throw new IllegalArgumentException("customerId must be positive");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        for (OrderItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.productId() <= 0) {
                throw new IllegalArgumentException("Each order item must have a positive productId");
            }
            if (item.quantity() == null || item.quantity() <= 0) {
                throw new IllegalArgumentException("Order item quantity must be positive");
            }
        }
    }

    private void requireCreated(Order order, String action) {
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new OrderStateException(
                    "Cannot " + action + " order " + order.getId()
                            + " because its status is " + order.getStatus()
            );
        }
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(), item.getProductId(), item.getQuantity(),
                        item.getUnitPrice(), item.getSubtotal()
                ))
                .toList();
        return new OrderResponse(
                order.getId(), order.getCustomerId(), order.getStatus(), order.getTotalAmount(),
                items, order.getCreatedAt(), order.getUpdatedAt()
        );
    }

}
