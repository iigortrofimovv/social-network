package com.example.socialnetwork.model;

import com.example.socialnetwork.dto.LikeRequest;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProfileLike {

    @EqualsAndHashCode.Include
    private Long id;

    private Long userId;
    private Long likedUserId;

    private OffsetDateTime likedAt;

    public static ProfileLike from(LikeRequest req) {
        return ProfileLike.builder()
                .userId(req.userId())
                .likedUserId(req.likedUserId())
                .likedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}