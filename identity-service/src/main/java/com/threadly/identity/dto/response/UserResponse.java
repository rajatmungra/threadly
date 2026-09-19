package com.threadly.identity.dto.response;

import com.threadly.identity.entity.User;
import com.threadly.identity.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String email,
    String displayName,
    String bio,
    UserRole role,
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getDisplayName(),
            user.getBio(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}
