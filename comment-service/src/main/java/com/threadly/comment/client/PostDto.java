package com.threadly.comment.client;

import java.util.UUID;

public record PostDto(
    UUID id,
    UUID authorId
) {
}
