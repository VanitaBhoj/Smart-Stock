package com.smartstock.controller;

import com.smartstock.dto.CreateReservationRequest;
import com.smartstock.dto.ReservationResponse;
import com.smartstock.service.ReservationService;
import com.smartstock.security.SmartStockPrincipal;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Reserve stock before order confirmation.")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Operation(summary = "Create reservation", description = "Reserves available stock for 10 minutes. Stock is moved from available to reserved within one transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reservation created",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":12,"productId":1,"quantity":2,"status":"ACTIVE","expiresAt":"2026-10-07T12:10:00Z","createdAt":"2026-10-07T12:00:00Z","updatedAt":"2026-10-07T12:00:00Z"}
                                    """))),
            @ApiResponse(responseCode = "400", description = "Product ID or quantity is invalid",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product or inventory does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or concurrent update conflict",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ResponseEntity<ReservationResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateReservationRequest.class),
                            examples = @ExampleObject(value = "{\"productId\":1,\"quantity\":2}"))
            )
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        ReservationResponse created = reservationService.create(request, principal.id());
        return ResponseEntity.created(URI.create("/api/reservations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reservation", description = "Returns a reservation. An active reservation past its expiry time is marked expired and its stock is released.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation found",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ReservationResponse findById(
            @Parameter(description = "Reservation ID", example = "12") @PathVariable Long id,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        return reservationService.findById(id, principal.id(), isAdmin(principal));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm reservation", description = "Transitions an active, unexpired reservation to CONFIRMED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation confirmed",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Reservation is expired or has an invalid state",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ReservationResponse confirm(
            @Parameter(description = "Reservation ID", example = "12") @PathVariable Long id,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        return reservationService.confirm(id, principal.id(), isAdmin(principal));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel reservation", description = "Transitions an active reservation to CANCELLED and returns its stock to available inventory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reservation cancelled",
                    content = @Content(schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Reservation has an invalid state",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ReservationResponse cancel(
            @Parameter(description = "Reservation ID", example = "12") @PathVariable Long id,
            @AuthenticationPrincipal SmartStockPrincipal principal
    ) {
        return reservationService.cancel(id, principal.id(), isAdmin(principal));
    }

    private boolean isAdmin(SmartStockPrincipal principal) {
        return principal.role() == com.smartstock.entity.UserRole.ADMIN;
    }
}
