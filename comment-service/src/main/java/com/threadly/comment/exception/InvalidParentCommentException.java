package com.threadly.comment.exception;

public class InvalidParentCommentException extends RuntimeException {
    public InvalidParentCommentException(String message) {
        super(message);
    }
}
