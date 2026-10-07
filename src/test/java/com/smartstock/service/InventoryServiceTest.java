package com.smartstock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartstock.dto.AdjustInventoryRequest;
import com.smartstock.dto.CreateInventoryRequest;
import com.smartstock.entity.Inventory;
import com.smartstock.entity.Product;
import com.smartstock.exception.DuplicateResourceException;
import com.smartstock.exception.InsufficientStockException;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.repository.InventoryRepository;
import com.smartstock.repository.ProductRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);

        inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(10);
        inventory.setReservedQuantity(2);
        ReflectionTestUtils.setField(inventory, "id", 5L);
        ReflectionTestUtils.setField(inventory, "version", 0L);
        ReflectionTestUtils.setField(inventory, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(inventory, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createRequiresExistingProductAndDefaultsReservedToZero() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.existsByProduct_Id(1L)).thenReturn(false);
        when(inventoryRepository.saveAndFlush(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = inventoryService.create(new CreateInventoryRequest(1L, 8L, null));

        assertThat(response.productId()).isEqualTo(1L);
        assertThat(response.availableQuantity()).isEqualTo(8L);
        assertThat(response.reservedQuantity()).isZero();
    }

    @Test
    void createRejectsUnknownProduct() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.create(new CreateInventoryRequest(99L, 0L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createRejectsDuplicateInventory() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(inventoryRepository.existsByProduct_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> inventoryService.create(new CreateInventoryRequest(1L, 0L, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Inventory already exists");
    }

    @Test
    void addAndRemoveStockAdjustAvailableQuantity() {
        when(inventoryRepository.findByProduct_Id(1L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);

        var added = inventoryService.addStock(1L, new AdjustInventoryRequest(5L));
        var removed = inventoryService.removeStock(1L, new AdjustInventoryRequest(3L));

        assertThat(added.availableQuantity()).isEqualTo(15L);
        assertThat(removed.availableQuantity()).isEqualTo(12L);
        assertThat(removed.reservedQuantity()).isEqualTo(2L);
        verify(inventoryRepository, org.mockito.Mockito.times(2)).saveAndFlush(inventory);
    }

    @Test
    void removeStockRejectsInsufficientQuantity() {
        when(inventoryRepository.findByProduct_Id(1L))
                .thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.removeStock(1L, new AdjustInventoryRequest(11L)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available 10");

        verify(inventoryRepository, never()).saveAndFlush(any(Inventory.class));
    }

    @Test
    void adjustSupportsPositiveAndNegativeDeltas() {
        when(inventoryRepository.findByProduct_Id(1L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);

        var response = inventoryService.adjust(1L, new AdjustInventoryRequest(-4L));

        assertThat(response.availableQuantity()).isEqualTo(6L);
    }
}
