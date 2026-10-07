package com.smartstock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartstock.dto.InventoryResponse;
import com.smartstock.exception.GlobalExceptionHandler;
import com.smartstock.exception.InsufficientStockException;
import com.smartstock.service.InventoryService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void createReturns201() throws Exception {
        when(inventoryService.create(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "availableQuantity": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createRejectsNegativeAvailableQuantity() throws Exception {
        mockMvc.perform(post("/api/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "availableQuantity": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.availableQuantity").exists());
    }

    @Test
    void getReturnsInventoryForProduct() throws Exception {
        when(inventoryService.findByProductId(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/inventory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    void adjustUpdatesInventory() throws Exception {
        when(inventoryService.adjust(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/inventory/1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": -2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(10));
    }

    @Test
    void addAndRemoveStockDelegateToService() throws Exception {
        when(inventoryService.addStock(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(sampleResponse());
        when(inventoryService.removeStock(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/inventory/1/add-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 3}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/inventory/1/remove-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 3}"))
                .andExpect(status().isOk());
    }

    @Test
    void removeStockReturns409WhenStockIsInsufficient() throws Exception {
        when(inventoryService.removeStock(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenThrow(new InsufficientStockException(
                        "Insufficient available stock for product 1: available 10, requested removal 11"
                ));

        mockMvc.perform(put("/api/inventory/1/remove-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 11}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Insufficient available stock for product 1: available 10, requested removal 11"
                ));
    }

    private static InventoryResponse sampleResponse() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new InventoryResponse(1L, 1L, 10L, 0L, 0L, now, now);
    }
}
