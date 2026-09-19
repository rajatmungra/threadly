package com.threadly.post.exception;

public class CommunityServiceUnavailableException extends RuntimeException {

    public CommunityServiceUnavailableException(String message) {
        super(message);
    }

    public CommunityServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
