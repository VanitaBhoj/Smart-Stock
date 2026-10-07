package com.smartstock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.entity.Inventory;
import com.smartstock.entity.Product;
import com.smartstock.entity.Reservation;
import com.smartstock.entity.ReservationStatus;
import com.smartstock.entity.UserRole;
import com.smartstock.security.SmartStockPrincipal;
import com.smartstock.dto.InventoryResponse;
import com.smartstock.repository.InventoryRepository;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.ReservationRepository;
import java.time.Instant;
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
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(properties = "reservation.expiration-scan-interval-ms=3600000")
@AutoConfigureMockMvc
@Import(IsolatedCacheConfiguration.class)
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

    @Autowired
    private ReservationService reservationService;

    @MockitoBean
    private InventoryReservationLock inventoryReservationLock;

    @Autowired
    private CacheManager cacheManager;

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
                    .isEqualTo("The resource changed during this request. Reload it and try again.");

            Inventory finalInventory = inventoryRepository.findByProduct_Id(productId).orElseThrow();
            assertThat(finalInventory.getAvailableQuantity()).isZero();
            assertThat(finalInventory.getReservedQuantity()).isEqualTo(1);
            assertThat(reservationRepository.count()).isEqualTo(1);
        } finally {
            startRequests.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void activeExpiredReservationReleasesStockAndExpires() {
        Inventory inventory = inventoryRepository.findByProduct_Id(productId).orElseThrow();
        inventory.setAvailableQuantity(0);
        inventory.setReservedQuantity(1);
        inventoryRepository.saveAndFlush(inventory);
        Reservation reservation = saveReservation(ReservationStatus.ACTIVE, Instant.now().minusSeconds(1));
        cacheManager.getCache("inventoryByProductId").put(
                productId,
                new InventoryResponse(inventory.getId(), productId, 0, 1, inventory.getVersion(),
                        inventory.getCreatedAt(), inventory.getUpdatedAt())
        );

        assertThat(reservationService.expireActiveReservations()).isEqualTo(1);

        Reservation saved = reservationRepository.findById(reservation.getId()).orElseThrow();
        Inventory updated = inventoryRepository.findByProduct_Id(productId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(updated.getAvailableQuantity()).isEqualTo(1);
        assertThat(updated.getReservedQuantity()).isZero();
        assertThat(cacheManager.getCache("inventoryByProductId").get(productId)).isNull();
    }

    @Test
    void activeReservationNotYetExpiredRemainsUnchanged() {
        Inventory inventory = inventoryRepository.findByProduct_Id(productId).orElseThrow();
        inventory.setAvailableQuantity(0);
        inventory.setReservedQuantity(1);
        inventoryRepository.saveAndFlush(inventory);
        Reservation reservation = saveReservation(
                ReservationStatus.ACTIVE,
                Instant.now().plusSeconds(600)
        );

        assertThat(reservationService.expireActiveReservations()).isZero();

        assertReservationAndStockUnchanged(reservation.getId(), ReservationStatus.ACTIVE);
    }

    @Test
    void confirmedReservationRemainsUnchangedAfterExpiryTime() {
        Inventory inventory = inventoryRepository.findByProduct_Id(productId).orElseThrow();
        inventory.setAvailableQuantity(0);
        inventory.setReservedQuantity(1);
        inventoryRepository.saveAndFlush(inventory);
        Reservation reservation = saveReservation(
                ReservationStatus.CONFIRMED,
                Instant.now().minusSeconds(600)
        );

        assertThat(reservationService.expireActiveReservations()).isZero();

        assertReservationAndStockUnchanged(reservation.getId(), ReservationStatus.CONFIRMED);
    }

    @Test
    void cancelledReservationRemainsUnchangedAfterExpiryTime() {
        Inventory inventory = inventoryRepository.findByProduct_Id(productId).orElseThrow();
        inventory.setAvailableQuantity(0);
        inventory.setReservedQuantity(1);
        inventoryRepository.saveAndFlush(inventory);
        Reservation reservation = saveReservation(
                ReservationStatus.CANCELLED,
                Instant.now().minusSeconds(600)
        );

        assertThat(reservationService.expireActiveReservations()).isZero();

        assertReservationAndStockUnchanged(reservation.getId(), ReservationStatus.CANCELLED);
    }

    private Reservation saveReservation(ReservationStatus status, Instant expiresAt) {
        Reservation reservation = new Reservation();
        reservation.setProduct(productRepository.findById(productId).orElseThrow());
        reservation.setQuantity(1);
        reservation.setStatus(status);
        reservation.setExpiresAt(expiresAt);
        return reservationRepository.saveAndFlush(reservation);
    }

    private void assertReservationAndStockUnchanged(
            Long reservationId,
            ReservationStatus expectedStatus
    ) {
        Reservation saved = reservationRepository.findById(reservationId).orElseThrow();
        Inventory inventory = inventoryRepository.findByProduct_Id(productId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(expectedStatus);
        assertThat(inventory.getAvailableQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isEqualTo(1);
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
        SmartStockPrincipal principal = new SmartStockPrincipal(900L, "customer", "{noop}test",
                UserRole.CUSTOMER, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        try {
            var response = mockMvc.perform(post("/api/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse();
            return new ReservationAttempt(response.getStatus(), response.getContentAsString());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private record ReservationAttempt(int status, String body) {
    }

}
