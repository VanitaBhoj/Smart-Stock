package com.smartstock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartstock.dto.CreateProductRequest;
import com.smartstock.dto.UpdateProductRequest;
import com.smartstock.entity.Product;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.CacheInvalidationRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "reservation.expiration-scan-interval-ms=3600000",
        "cache.invalidation-scan-interval-ms=3600000"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:productcache;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@Import(IsolatedCacheConfiguration.class)
class ProductCachingIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheInvalidationRepository invalidationRepository;

    @Autowired
    private CacheInvalidationProcessor invalidationProcessor;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearState() {
        productRepository.deleteAllInBatch();
        invalidationRepository.deleteAllInBatch();
        cacheManager.getCache("productById").clear();
        cacheManager.getCache("productBySku").clear();
    }

    @Test
    void repeatedIdAndSkuReadsUseTheCachedProductResponse() {
        var created = productService.create(new CreateProductRequest(
                "Desk Lamp", "LAMP-OLD", null, new BigDecimal("35.00"), "Home", true
        ));
        productService.findById(created.id());
        productService.findBySku("LAMP-OLD");
        Product databaseProduct = productRepository.findById(created.id()).orElseThrow();
        databaseProduct.setName("Changed directly in database");
        databaseProduct.setSku("LAMP-NEW");
        productRepository.saveAndFlush(databaseProduct);

        assertThat(productService.findById(created.id()).name()).isEqualTo("Desk Lamp");
        assertThat(productService.findBySku("LAMP-OLD").name()).isEqualTo("Desk Lamp");
        assertThat(productService.findBySku("LAMP-NEW").name())
                .isEqualTo("Changed directly in database");
    }

    @Test
    void updateRefreshesIdCacheAndEvictsSkuEntriesThenDeleteEvictsBothCaches() {
        var created = productService.create(new CreateProductRequest(
                "Desk Lamp", "LAMP-OLD", null, new BigDecimal("35.00"), "Home", true
        ));
        productService.findBySku("LAMP-OLD");

        var updated = productService.update(created.id(), new UpdateProductRequest(
                "Reading Lamp", "LAMP-NEW", null, new BigDecimal("42.00"), "Lighting", true
        ));
        invalidationProcessor.processPendingInvalidations();

        assertThat(cacheManager.getCache("productById").get(created.id())).isNull();
        assertThat(productService.findById(created.id())).isEqualTo(updated);
        assertThat(cacheManager.getCache("productBySku").get("LAMP-OLD")).isNull();

        productService.findBySku("LAMP-NEW");
        productService.delete(created.id());
        invalidationProcessor.processPendingInvalidations();

        assertThat(cacheManager.getCache("productById").get(created.id())).isNull();
        assertThat(cacheManager.getCache("productBySku").get("LAMP-NEW")).isNull();
    }

    @Test
    void openApiDocumentListsProductInventoryReservationAndOrderEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/products")))
                .andExpect(content().string(containsString("/api/inventory")))
                .andExpect(content().string(containsString("/api/reservations")))
                .andExpect(content().string(containsString("/api/orders")));
    }
}
