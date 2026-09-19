package com.threadly.post.dto.response;

import java.util.UUID;

public record VoteResponse(
    UUID postId,
    int value,
    int score
) {
}
