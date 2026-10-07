package com.smartstock.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartstock.dto.ReservationResponse;
import com.smartstock.entity.ReservationStatus;
import com.smartstock.exception.GlobalExceptionHandler;
import com.smartstock.exception.ReservationStateException;
import com.smartstock.service.ReservationService;
import com.smartstock.entity.UserRole;
import com.smartstock.security.SmartStockPrincipal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    private SmartStockPrincipal principal;

    @BeforeEach
    void authenticateCustomer() {
        principal = new SmartStockPrincipal(900L, "customer", "{noop}test", UserRole.CUSTOMER, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReturns201AndCleanResponseDto() throws Exception {
        when(reservationService.create(any(), eq(900L))).thenReturn(sample(ReservationStatus.ACTIVE));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void createRejectsNonPositiveQuantity() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.quantity").exists());
    }

    @Test
    void getReturnsReservation() throws Exception {
        when(reservationService.findById(1L, 900L, false)).thenReturn(sample(ReservationStatus.ACTIVE));

        mockMvc.perform(get("/api/reservations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void confirmAndCancelReturnUpdatedReservations() throws Exception {
        when(reservationService.confirm(1L, 900L, false)).thenReturn(sample(ReservationStatus.CONFIRMED));
        when(reservationService.cancel(2L, 900L, false)).thenReturn(sample(ReservationStatus.CANCELLED));

        mockMvc.perform(post("/api/reservations/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(post("/api/reservations/2/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void invalidStateTransitionReturns409() throws Exception {
        when(reservationService.confirm(1L, 900L, false))
                .thenThrow(new ReservationStateException(
                        "Cannot confirm reservation 1 because its status is CANCELLED"
                ));

        mockMvc.perform(post("/api/reservations/1/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Cannot confirm reservation 1 because its status is CANCELLED"
                ));
    }

    private static ReservationResponse sample(ReservationStatus status) {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        return new ReservationResponse(1L, 1L, 2L, status, now.plusSeconds(600), now, now);
    }
}
