package com.example.socialnetwork.repository.projection;

import java.time.OffsetDateTime;

public record VisitorView(Long visitorId, OffsetDateTime lastVisit) {
}
