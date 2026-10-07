package com.smartstock.controller;

import com.smartstock.dto.CreateOrderRequest;
import com.smartstock.dto.OrderResponse;
import com.smartstock.service.OrderService;
import com.smartstock.security.SmartStockPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Create and manage customer orders.")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Create order", description = "Reserves stock for each line, snapshots current product prices, calculates totals, and creates an order in CREATED status. The authenticated customer owns the order.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":101,"customerId":900,"status":"CREATED","totalAmount":37.00,"items":[{"id":1,"productId":1,"quantity":2,"unitPrice":12.50,"subtotal":25.00},{"id":2,"productId":2,"quantity":3,"unitPrice":4.00,"subtotal":12.00}],"createdAt":"2026-10-07T12:00:00Z","updatedAt":"2026-10-07T12:00:00Z"}
                                    """))),
            @ApiResponse(responseCode = "400", description = "Customer or order item data is invalid",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "One or more products do not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Product inactive, insufficient stock, or concurrent update conflict",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ResponseEntity<OrderResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateOrderRequest.class),
                            examples = @ExampleObject(value = """
                                    {"items":[{"productId":1,"quantity":2},{"productId":2,"quantity":3}]}
                                    """))
            )
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        OrderResponse created = orderService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order", description = "Returns an order and its line items by order ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public OrderResponse findById(
            @Parameter(description = "Order ID", example = "101") @PathVariable Long id,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        return orderService.findById(id, principal.id(), isAdmin(principal));
    }

    @GetMapping
    @Operation(summary = "List all orders", description = "Admin-only paginated order listing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = OrderResponse.class))))
    })
    public List<OrderResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return orderService.findAll(page, size);
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "List customer orders", description = "Returns a page of orders for the specified customer, newest first (default 50, maximum 100).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Customer orders returned",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = OrderResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Customer ID must be positive",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public List<OrderResponse> findByCustomerId(
            @Parameter(description = "Customer ID", example = "900") @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        return orderService.findByCustomerId(customerId, principal.id(), isAdmin(principal), page, size);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm order", description = "Confirms all linked reservations and transitions a CREATED order to CONFIRMED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order confirmed",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Order or reservation has an invalid state",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public OrderResponse confirm(
            @Parameter(description = "Order ID", example = "101") @PathVariable Long id,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        return orderService.confirm(id, principal.id(), isAdmin(principal));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancels linked active reservations, releases stock, and transitions a CREATED order to CANCELLED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Order or reservation has an invalid state",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public OrderResponse cancel(
            @Parameter(description = "Order ID", example = "101") @PathVariable Long id,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        return orderService.cancel(id, principal.id(), isAdmin(principal));
    }

    private boolean isAdmin(SmartStockPrincipal principal) {
        return principal.role() == com.smartstock.entity.UserRole.ADMIN;
    }
}
