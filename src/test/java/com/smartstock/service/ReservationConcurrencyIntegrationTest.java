package com.smartstock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.entity.Inventory;
import com.smartstock.entity.Product;
import com.smartstock.repository.InventoryRepository;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.ReservationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationConcurrencyIntegrationTest.TestCacheConfiguration.class)
class ReservationConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        inventoryRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        Product product = new Product();
        product.setName("Concurrency test product");
        product.setSku("CONCURRENT-1");
        product.setPrice(new BigDecimal("2.50"));
        productId = productRepository.saveAndFlush(product).getId();

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setAvailableQuantity(1);
        inventory.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(inventory);
    }

    @Test
    void onlyOneConcurrentReservationCanReserveTheLastUnit() throws Exception {
        CountDownLatch bothRequestsReady = new CountDownLatch(2);
        CountDownLatch startRequests = new CountDownLatch(1);
        String body = "{\"productId\":" + productId + ",\"quantity\":1}";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationAttempt> first = executor.submit(() -> reserveWhenReleased(
                    body, bothRequestsReady, startRequests
            ));
            Future<ReservationAttempt> second = executor.submit(() -> reserveWhenReleased(
                    body, bothRequestsReady, startRequests
            ));

            assertThat(bothRequestsReady.await(10, TimeUnit.SECONDS)).isTrue();
            startRequests.countDown();

            List<ReservationAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
            assertThat(attempts).extracting(ReservationAttempt::status)
                    .containsExactlyInAnyOrder(201, 409);
            ReservationAttempt rejected = attempts.stream()
                    .filter(attempt -> attempt.status() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(objectMapper.readTree(rejected.body()).path("message").asText())
                    .isEqualTo(
                            "Inventory changed while processing the reservation; reload stock and retry"
                    );

            Inventory finalInventory = inventoryRepository.findByProduct_Id(productId).orElseThrow();
            assertThat(finalInventory.getAvailableQuantity()).isZero();
            assertThat(finalInventory.getReservedQuantity()).isEqualTo(1);
            assertThat(reservationRepository.count()).isEqualTo(1);
        } finally {
            startRequests.countDown();
            executor.shutdownNow();
        }
    }

    private ReservationAttempt reserveWhenReleased(
            String body,
            CountDownLatch requestsReady,
            CountDownLatch startRequests
    ) throws Exception {
        requestsReady.countDown();
        if (!startRequests.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent requests");
        }
        return reserve(body);
    }

    private ReservationAttempt reserve(String body) throws Exception {
        var response = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();
        return new ReservationAttempt(response.getStatus(), response.getContentAsString());
    }

    private record ReservationAttempt(int status, String body) {
    }

    @TestConfiguration
    static class TestCacheConfiguration {

        @Bean
        @Primary
        CacheManager testCacheManager() {
            return new ConcurrentMapCacheManager(
                    "productById", "productBySku", "inventoryByProductId"
            );
        }
    }
}
