package com.example.socialnetwork.repository;

import com.example.socialnetwork.model.ProfileLike;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class ProfileLikeRepository {

    private final JdbcTemplate jdbc;

    /**
     * Returns true if the like was inserted, false if it already existed.
     * Uses INSERT ... ON CONFLICT DO NOTHING to avoid a separate SELECT.
     */
    public boolean save(ProfileLike like) {
        int rows = jdbc.update("""
                        INSERT INTO profile_likes (user_id, liked_user_id, liked_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (user_id, liked_user_id) DO NOTHING
                        """,
                like.getUserId(),
                like.getLikedUserId(),
                Timestamp.from(like.getLikedAt().toInstant())
        );
        return rows > 0;
    }

    public void batchInsert(List<ProfileLike> likes) {
        List<ProfileLike> safeList = List.copyOf(likes);
        jdbc.batchUpdate("""
                        INSERT INTO profile_likes (user_id, liked_user_id, liked_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (user_id, liked_user_id) DO NOTHING
                        """,
                safeList,
                safeList.size(),
                (ps, l) -> {
                    ps.setLong(1, l.getUserId());
                    ps.setLong(2, l.getLikedUserId());
                    ps.setObject(3, l.getLikedAt());
                }
        );
    }
}