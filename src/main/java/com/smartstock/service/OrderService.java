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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final OutboxService outboxService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            ReservationRepository reservationRepository,
            ReservationService reservationService,
            OutboxService outboxService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.outboxService = outboxService;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Long customerId) {
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
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(BigDecimal.ZERO.setScale(2));
        order = orderRepository.saveAndFlush(order);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.items()) {
            Product product = products.get(itemRequest.productId());
            ReservationResponse reservation = reservationService.create(
                    new CreateReservationRequest(itemRequest.productId(), itemRequest.quantity()), customerId
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
        recordOrderEvent("ORDER_CREATED", order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse findById(Long id, Long actorId, boolean admin) {
        Order order = getOrder(id);
        requireOwnerOrAdmin(order, actorId, admin);
        return toResponse(order);
    }

    @Transactional
    public List<OrderResponse> findByCustomerId(
            Long customerId, Long actorId, boolean admin, int page, int size
    ) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("customerId must be positive");
        }
        if (!admin && !customerId.equals(actorId)) {
            throw new ResourceNotFoundException("Orders not found for customer: " + customerId);
        }
        validatePage(page, size);
        Page<Order> ordersPage = orderRepository.findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return withItems(ordersPage.getContent());
    }

    @Transactional
    public List<OrderResponse> findAll(int page, int size) {
        validatePage(page, size);
        return withItems(orderRepository.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("id")))).getContent());
    }

    private List<OrderResponse> withItems(List<Order> orders) {
        List<Long> ids = orders.stream().map(Order::getId).toList();
        if (ids.isEmpty()) return List.of();
        Map<Long, Order> withItems = orderRepository.findAllWithItemsByIdIn(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        return ids.stream().map(withItems::get).filter(java.util.Objects::nonNull)
                .map(this::toResponse).toList();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
    }

    @Transactional
    public OrderResponse confirm(Long id, Long actorId, boolean admin) {
        Order order = getOrder(id);
        requireOwnerOrAdmin(order, actorId, admin);
        requireCreated(order, "confirm");
        for (OrderItem item : order.getItems()) {
            ReservationResponse reservation = reservationService.confirm(item.getReservationId(), actorId, admin);
            if (reservation.status() != ReservationStatus.CONFIRMED) {
                throw new ReservationStateException(
                        "Reservation " + reservation.id() + " could not be confirmed"
                );
            }
        }
        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.saveAndFlush(order);
        recordOrderEvent("ORDER_CONFIRMED", saved);
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse cancel(Long id, Long actorId, boolean admin) {
        Order order = getOrder(id);
        requireOwnerOrAdmin(order, actorId, admin);
        requireCreated(order, "cancel");
        for (OrderItem item : order.getItems()) {
            ReservationResponse reservation = reservationService.cancel(item.getReservationId(), actorId, admin);
            if (reservation.status() != ReservationStatus.CANCELLED
                    && reservation.status() != ReservationStatus.EXPIRED) {
                throw new ReservationStateException(
                        "Reservation " + reservation.id() + " could not be cancelled"
                );
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.saveAndFlush(order);
        recordOrderEvent("ORDER_CANCELLED", saved);
        return toResponse(saved);
    }

    private void recordOrderEvent(String eventType, Order order) {
        outboxService.record(eventType, "ORDER", order.getId(), Map.of(
                "orderId", order.getId(),
                "customerId", order.getCustomerId(),
                "status", order.getStatus(),
                "totalAmount", order.getTotalAmount()
        ));
    }

    private Order getOrder(Long id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private void validateRequest(CreateOrderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
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

    private void requireOwnerOrAdmin(Order order, Long actorId, boolean admin) {
        if (!admin && !order.getCustomerId().equals(actorId)) {
            throw new ResourceNotFoundException("Order not found with id: " + order.getId());
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
