package com.example.socialnetwork.dto;

import jakarta.validation.constraints.NotNull;

public record LikeRequest(
        @NotNull Long userId,
        @NotNull Long likedUserId
) {
}