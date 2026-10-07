package com.smartstock.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.dto.ApiError;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Value("${app.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    if (securityEnabled) {
                        authorize.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/register").permitAll()
                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                        "/api/products/**", "/api/inventory/**").permitAll()
                                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/products/**")
                                        .hasRole("ADMIN")
                                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/products/**",
                                        "/api/inventory/**").hasRole("ADMIN")
                                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/products/**")
                                        .hasRole("ADMIN")
                                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/inventory/**")
                                        .hasRole("ADMIN")
                                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/reservations")
                                        .hasRole("CUSTOMER")
                                .requestMatchers("/api/reservations/**")
                                        .hasAnyRole("CUSTOMER", "ADMIN")
                                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/orders")
                                        .hasRole("CUSTOMER")
                                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/orders")
                                        .hasRole("ADMIN")
                                .requestMatchers("/api/orders/**")
                                        .hasAnyRole("CUSTOMER", "ADMIN")
                                .anyRequest().denyAll();
                    } else {
                        authorize.anyRequest().permitAll();
                    }
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getOutputStream(), new ApiError(
                            Instant.now(), HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED",
                            "Authentication is required.", request.getRequestURI(), null
                    ));
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getOutputStream(), new ApiError(
                            Instant.now(), HttpStatus.FORBIDDEN.value(), "FORBIDDEN",
                            "You are not authorized to access this resource.", request.getRequestURI(), null
                    ));
                }))
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
