package com.example.socialnetwork.model;

import com.example.socialnetwork.dto.VisitRequest;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProfileVisit {

    @EqualsAndHashCode.Include
    private Long id;

    private Long visitorId;
    private Long visitedId;

    private OffsetDateTime visitedAt;

    public static ProfileVisit from(VisitRequest req) {
        return ProfileVisit.builder()
                .visitorId(req.visitorId())
                .visitedId(req.visitedId())
                .visitedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}