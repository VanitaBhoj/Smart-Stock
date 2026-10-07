package com.smartstock.controller;

import com.smartstock.dto.CreateProductRequest;
import com.smartstock.dto.ProductResponse;
import com.smartstock.dto.UpdateProductRequest;
import com.smartstock.service.ProductService;
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
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Create and manage catalog products.")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @Operation(summary = "Create product", description = "Adds a product to the catalog.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class),
                            examples = @ExampleObject(value = """
                                    {"id":1,"name":"Desk Lamp","sku":"LAMP-001","description":"LED desk lamp","price":35.00,"category":"Home","active":true,"createdAt":"2026-10-07T12:00:00Z","updatedAt":"2026-10-07T12:00:00Z"}
                                    """))),
            @ApiResponse(responseCode = "400", description = "Invalid product data",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "SKU already exists",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ResponseEntity<ProductResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateProductRequest.class),
                            examples = @ExampleObject(value = """
                                    {"name":"Desk Lamp","sku":"LAMP-001","description":"LED desk lamp","price":35.00,"category":"Home","active":true}
                                    """))
            )
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse created = productService.create(request);
        return ResponseEntity
                .created(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List products", description = "Returns a page of catalog products (default 50, maximum 100).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products returned",
                    content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                            schema = @Schema(implementation = ProductResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public List<ProductResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return productService.findAll(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns one product using its database ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "400", description = "ID is not a number",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ProductResponse findById(
            @Parameter(description = "Product database ID", example = "1") @PathVariable Long id
    ) {
        return productService.findById(id);
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU", description = "Returns one product using its unique SKU.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "SKU does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ProductResponse findBySku(
            @Parameter(description = "Unique product SKU", example = "LAMP-001") @PathVariable String sku
    ) {
        return productService.findBySku(sku);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Replaces editable catalog fields for a product.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid product data",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "SKU conflict",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ProductResponse update(
            @Parameter(description = "Product database ID", example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateProductRequest.class),
                            examples = @ExampleObject(value = """
                                    {"name":"Reading Lamp","sku":"LAMP-001","description":"Adjustable LED lamp","price":42.00,"category":"Home","active":true}
                                    """))
            )
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Deletes a product that is not referenced by other records.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted"),
            @ApiResponse(responseCode = "404", description = "Product does not exist",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Product is referenced by existing records",
                    content = @Content(schema = @Schema(implementation = com.smartstock.dto.ApiError.class)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Product database ID", example = "1") @PathVariable Long id
    ) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
