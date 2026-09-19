package com.threadly.comment.dto.response;

import java.util.UUID;

public record VoteResponse(
    UUID commentId,
    int value,
    int score
) {
}
