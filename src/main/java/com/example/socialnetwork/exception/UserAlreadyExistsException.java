package com.example.socialnetwork.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
