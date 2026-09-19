package com.threadly.comment.dto.request;

import com.threadly.comment.validation.ValidVoteValue;
import jakarta.validation.constraints.NotNull;

public record VoteRequest(
    @NotNull(message = "Vote value is required")
    @ValidVoteValue
    Integer value
) {
}
