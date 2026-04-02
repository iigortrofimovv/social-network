package com.example.socialnetwork.dto;

import jakarta.validation.constraints.NotNull;

public record VisitRequest(
        @NotNull Long visitorId,
        @NotNull Long visitedId
) {
}