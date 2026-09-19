package com.threadly.comment.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String code,
    String message,
    String path,
    Instant timestamp,
    Map<String, String> errors
) {
    public ErrorResponse(int status, String code, String message, String path, Instant timestamp) {
        this(status, code, message, path, timestamp, null);
    }
}
