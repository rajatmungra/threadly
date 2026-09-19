package com.threadly.community.exception;

public class DuplicateCommunityNameException extends RuntimeException {

    public DuplicateCommunityNameException(String message) {
        super(message);
    }

    public DuplicateCommunityNameException(String message, Throwable cause) {
        super(message, cause);
    }
}
