package com.smartstock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartstock.dto.CreateProductRequest;
import com.smartstock.dto.ProductResponse;
import com.smartstock.dto.UpdateProductRequest;
import com.smartstock.entity.Product;
import com.smartstock.exception.DuplicateResourceException;
import com.smartstock.exception.ResourceNotFoundException;
import com.smartstock.repository.ProductRepository;
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
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @InjectMocks
    private ProductService productService;

    private Product existing;

    @BeforeEach
    void setUp() {
        existing = product(1L, "Wireless Mouse", "MOUSE-001", new BigDecimal("19.99"), true);
    }

    @Test
    void createSavesProductAndDefaultsActiveToTrue() {
        CreateProductRequest request = new CreateProductRequest(
                "Wireless Mouse",
                "MOUSE-001",
                "Ergonomic mouse",
                new BigDecimal("19.99"),
                "Electronics",
                null
        );
        when(productRepository.existsBySku("MOUSE-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
            ReflectionTestUtils.setField(saved, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
            return saved;
        });

        ProductResponse response = productService.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sku()).isEqualTo("MOUSE-001");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createThrowsWhenSkuExists() {
        CreateProductRequest request = new CreateProductRequest(
                "Wireless Mouse",
                "MOUSE-001",
                null,
                new BigDecimal("19.99"),
                null,
                true
        );
        when(productRepository.existsBySku("MOUSE-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("MOUSE-001");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findAllMapsEntitiesToResponses() {
        when(productRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(existing)));

        List<ProductResponse> products = productService.findAll(0, 50);

        assertThat(products).hasSize(1);
        assertThat(products.getFirst().sku()).isEqualTo("MOUSE-001");
    }

    @Test
    void updateRejectsDuplicateSku() {
        UpdateProductRequest request = new UpdateProductRequest(
                "Wireless Mouse",
                "MOUSE-002",
                null,
                new BigDecimal("21.00"),
                "Electronics",
                true
        );
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("MOUSE-002", 1L)).thenReturn(true);

        assertThatThrownBy(() -> productService.update(1L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updatePersistsAndReturnsCurrentProductValues() {
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Mouse",
                "MOUSE-NEW",
                "Updated description",
                new BigDecimal("24.50"),
                "Accessories",
                false
        );
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.existsBySkuAndIdNot("MOUSE-NEW", 1L)).thenReturn(false);
        when(productRepository.save(existing)).thenReturn(existing);

        ProductResponse response = productService.update(1L, request);

        assertThat(response.name()).isEqualTo("Updated Mouse");
        assertThat(response.sku()).isEqualTo("MOUSE-NEW");
        assertThat(response.price()).isEqualByComparingTo("24.50");
        assertThat(response.active()).isFalse();
        verify(productRepository).save(existing);
    }

    @Test
    void deleteRemovesExistingProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

        productService.delete(1L);

        verify(productRepository).delete(existing);
    }

    private static Product product(Long id, String name, String sku, BigDecimal price, boolean active) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setName(name);
        product.setSku(sku);
        product.setPrice(price);
        product.setActive(active);
        ReflectionTestUtils.setField(product, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(product, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
        return product;
    }
}
