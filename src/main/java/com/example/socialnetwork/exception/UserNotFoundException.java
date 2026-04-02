package com.example.socialnetwork.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long userid) {
        super("User with %d not found".formatted(userid));

    }
}
