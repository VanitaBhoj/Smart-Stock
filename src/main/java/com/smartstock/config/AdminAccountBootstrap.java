package com.smartstock.config;

import com.smartstock.entity.User;
import com.smartstock.entity.UserRole;
import com.smartstock.repository.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminAccountBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${SMARTSTOCK_ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${SMARTSTOCK_ADMIN_PASSWORD:}")
    private String adminPassword;

    public AdminAccountBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        boolean hasUsername = adminUsername != null && !adminUsername.isBlank();
        boolean hasPassword = adminPassword != null && !adminPassword.isBlank();
        if (!hasUsername && !hasPassword) return;
        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException("Set both SMARTSTOCK_ADMIN_USERNAME and SMARTSTOCK_ADMIN_PASSWORD");
        }
        if (adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("SMARTSTOCK_ADMIN_PASSWORD must be at most 72 UTF-8 bytes");
        }
        if (adminPassword.length() < 12) {
            throw new IllegalStateException("SMARTSTOCK_ADMIN_PASSWORD must be at least 12 characters");
        }

        String normalized = adminUsername.trim().toLowerCase(Locale.ROOT);
        userRepository.findByUsername(normalized).ifPresentOrElse(existing -> {
            if (existing.getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("Configured admin username belongs to a non-admin account");
            }
            if (!passwordEncoder.matches(adminPassword, existing.getPasswordHash())) {
                existing.setPasswordHash(passwordEncoder.encode(adminPassword));
                userRepository.save(existing);
            }
        }, () -> userRepository.save(new User(normalized, passwordEncoder.encode(adminPassword),
                UserRole.ADMIN, clock.instant())));
    }
}
