package com.threadly.comment.exception;

public class PostServiceUnavailableException extends RuntimeException {

    public PostServiceUnavailableException(String message) {
        super(message);
    }

    public PostServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
