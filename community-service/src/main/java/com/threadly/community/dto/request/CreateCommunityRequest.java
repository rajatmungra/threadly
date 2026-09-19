package com.threadly.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CreateCommunityRequest(
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Name must contain only lowercase letters, numbers, and underscores")
    String name,

    @NotBlank(message = "Display name is required")
    @Size(min = 3, max = 100, message = "Display name must be between 3 and 100 characters")
    String displayName,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description
) {
    public CreateCommunityRequest {
        name = name != null ? name.trim().toLowerCase(Locale.ROOT) : null;
        displayName = displayName != null ? displayName.trim() : null;
        description = description != null ? description.trim() : null;
    }
}
