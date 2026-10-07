package com.smartstock.controller;

import com.smartstock.dto.AdjustInventoryRequest;
import com.smartstock.dto.CreateInventoryRequest;
import com.smartstock.dto.InventoryResponse;
import com.smartstock.service.InventoryService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Read stock and adjust available inventory.")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @Operation(summary = "Create inventory", description = "Creates the inventory record for a product.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inventory created",
                    content = @Content(schema = @Schema(implementation = InventoryResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":1,"productId":1,"availableQuantity":10,"reservedQuantity":0,"version":0,"createdAt":"2026-10-07T12:00:00Z","updatedAt":"2026-10-07T12:00:00Z"}
                                    """))),
            @ApiResponse(responseCode = "400", description = "Invalid quantities",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Inventory already exists",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ResponseEntity<InventoryResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateInventoryRequest.class),
                            examples = @ExampleObject(value = """
                                    {"productId":1,"availableQuantity":10}
                                    """))
            )
            @Valid @RequestBody CreateInventoryRequest request
    ) {
        InventoryResponse created = inventoryService.create(request);
        return ResponseEntity.created(URI.create("/api/inventory/" + created.productId()))
                .body(created);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory", description = "Returns inventory by product ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory found",
                    content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Inventory does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "400", description = "Product ID is not a number",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public InventoryResponse findByProductId(
            @Parameter(description = "ID of the product", example = "1") @PathVariable Long productId
    ) {
        return inventoryService.findByProductId(productId);
    }

    @PutMapping("/{productId}/adjust")
    @Operation(summary = "Adjust available inventory", description = "Applies a signed delta to available stock. Negative values remove stock and cannot reduce it below zero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory adjusted",
                    content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid adjustment",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Inventory does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or concurrent update",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public InventoryResponse adjust(
            @Parameter(description = "ID of the product", example = "1") @PathVariable Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = AdjustInventoryRequest.class),
                            examples = @ExampleObject(value = "{\"quantity\":-2}"))
            )
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return inventoryService.adjust(productId, request);
    }

    @PutMapping("/{productId}/add-stock")
    @Operation(summary = "Add stock", description = "Increases available inventory by a positive quantity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock added",
                    content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Quantity must be positive",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Inventory does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Concurrent update conflict",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public InventoryResponse addStock(
            @Parameter(description = "ID of the product", example = "1") @PathVariable Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = AdjustInventoryRequest.class),
                            examples = @ExampleObject(value = "{\"quantity\":5}"))
            )
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return inventoryService.addStock(productId, request);
    }

    @PutMapping("/{productId}/remove-stock")
    @Operation(summary = "Remove stock", description = "Decreases available inventory by a positive quantity if enough stock remains.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock removed",
                    content = @Content(schema = @Schema(implementation = InventoryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Quantity must be positive",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Inventory does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Insufficient stock or concurrent update",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public InventoryResponse removeStock(
            @Parameter(description = "ID of the product", example = "1") @PathVariable Long productId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = AdjustInventoryRequest.class),
                            examples = @ExampleObject(value = "{\"quantity\":2}"))
            )
            @Valid @RequestBody AdjustInventoryRequest request
    ) {
        return inventoryService.removeStock(productId, request);
    }
}
