package com.threadly.post.dto.request;

import com.threadly.post.validation.ValidVoteValue;
import jakarta.validation.constraints.NotNull;

public record VoteRequest(
    @NotNull(message = "Vote value is required")
    @ValidVoteValue
    Integer value
) {
}
