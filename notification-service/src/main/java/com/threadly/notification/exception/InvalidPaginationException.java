package com.threadly.notification.exception;

import java.util.Collections;
import java.util.Map;

public class InvalidPaginationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidPaginationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors != null ? Collections.unmodifiableMap(fieldErrors) : Collections.emptyMap();
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
