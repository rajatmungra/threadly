package com.threadly.community.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CommunityResponse(
    UUID id,
    String name,
    String displayName,
    String description,
    UUID createdBy,
    long memberCount,
    Instant createdAt
) {
}
