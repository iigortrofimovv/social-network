package com.example.socialnetwork.dto;

import jakarta.validation.constraints.*;

import java.util.Map;

public record CreateUserRequest(

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotNull
        @Min(0)
        @Max(120)
        Integer age,

        Map<String, Object> metadata
) {
    public CreateUserRequest {
        metadata = metadata != null ? metadata : Map.of();
    }
}