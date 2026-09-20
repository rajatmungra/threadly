package com.threadly.identity.service;

import com.threadly.identity.dto.request.RegisterRequest;
import com.threadly.identity.dto.response.UserResponse;
import com.threadly.identity.entity.User;
import com.threadly.identity.entity.UserRole;
import com.threadly.identity.exception.DuplicateEmailException;
import com.threadly.identity.exception.DuplicateUsernameException;
import com.threadly.identity.exception.UserNotFoundException;
import com.threadly.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedUsername = request.username().trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String normalizedDisplayName = request.displayName() != null ? request.displayName().trim() : null;
        if (normalizedDisplayName != null && normalizedDisplayName.isEmpty()) {
            normalizedDisplayName = null;
        }

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new DuplicateUsernameException("Username already exists");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(normalizedDisplayName);
        user.setRole(UserRole.USER);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return UserResponse.from(savedUser);
        } catch (DataIntegrityViolationException ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
            Throwable rootCause = ex.getRootCause();
            if (rootCause != null && rootCause.getMessage() != null) {
                msg += " " + rootCause.getMessage().toLowerCase(Locale.ROOT);
            }

            if (msg.contains("uk_users_username")) {
                throw new DuplicateUsernameException("Username already exists");
            }
            if (msg.contains("uk_users_email")) {
                throw new DuplicateEmailException("Email already exists");
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found"));
        return UserResponse.from(user);
    }
}
