package com.example.socialnetwork.dto;

import com.example.socialnetwork.model.User;

import java.util.Map;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        Integer age,
        Map<String, Object> metadata
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getAge(),
                user.getMetadata().fields()
        );
    }
}