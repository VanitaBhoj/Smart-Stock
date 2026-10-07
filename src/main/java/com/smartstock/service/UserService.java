package com.smartstock.service;

import com.smartstock.dto.RegisterCustomerRequest;
import com.smartstock.dto.UserResponse;
import com.smartstock.entity.User;
import com.smartstock.entity.UserRole;
import com.smartstock.exception.DuplicateResourceException;
import com.smartstock.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserResponse registerCustomer(RegisterCustomerRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (username.length() < 3) {
            throw new IllegalArgumentException("Username must contain at least 3 non-space characters");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes");
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username is already registered");
        }
        User user = new User(username, passwordEncoder.encode(request.password()), UserRole.CUSTOMER,
                clock.instant());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Username is already registered");
        }
        return new UserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}
