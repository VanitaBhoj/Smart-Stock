package com.smartstock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartstock.dto.ProductResponse;
import com.smartstock.exception.DuplicateResourceException;
import com.smartstock.exception.GlobalExceptionHandler;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void createReturns201() throws Exception {
        when(productService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Mouse",
                                  "sku": "MOUSE-001",
                                  "description": "Ergonomic mouse",
                                  "price": 19.99,
                                  "category": "Electronics"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/1"))
                .andExpect(jsonPath("$.sku").value("MOUSE-001"));
    }

    @Test
    void createReturns400WhenPriceIsInvalid() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Mouse",
                                  "sku": "MOUSE-001",
                                  "price": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void createReturns400WhenNameOrSkuIsBlank() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "sku": "",
                                  "price": 19.99
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.sku").exists());
    }

    @Test
    void createReturns409WhenSkuDuplicates() throws Exception {
        when(productService.create(any()))
                .thenThrow(new DuplicateResourceException("SKU already exists: MOUSE-001"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Mouse",
                                  "sku": "MOUSE-001",
                                  "price": 19.99
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void findAllReturns200() throws Exception {
        when(productService.findAll(0, 50)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        when(productService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void unexpectedErrorsReturnSanitizedConsistentResponse() throws Exception {
        when(productService.findById(77L))
                .thenThrow(new IllegalStateException("jdbc:postgresql://internal-db/password"));

        mockMvc.perform(get("/api/products/77"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/products/77"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("postgresql"))
                ));
    }

    @Test
    void findByIdReturns200() throws Exception {
        when(productService.findById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findBySkuReturns200() throws Exception {
        when(productService.findBySku("MOUSE-001")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/products/sku/MOUSE-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void findBySkuReturns404WhenMissing() throws Exception {
        when(productService.findBySku("UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Product not found with sku: UNKNOWN"));

        mockMvc.perform(get("/api/products/sku/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with sku: UNKNOWN"));
    }

    @Test
    void updateReturns200() throws Exception {
        when(productService.update(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Mouse",
                                  "sku": "MOUSE-001",
                                  "price": 19.99,
                                  "category": "Electronics",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteReturns204() throws Exception {
        doNothing().when(productService).delete(1L);

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        doThrow(new ResourceNotFoundException("Product not found with id: 99"))
                .when(productService).delete(99L);

        mockMvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    private static ProductResponse sampleResponse() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ProductResponse(
                1L,
                "Wireless Mouse",
                "MOUSE-001",
                "Ergonomic mouse",
                new BigDecimal("19.99"),
                "Electronics",
                true,
                now,
                now
        );
    }
}
