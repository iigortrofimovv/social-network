package com.example.socialnetwork.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class FraudService {

    private final NamedParameterJdbcTemplate jdbc;

    @Async("fraudCheckExecutor")
    public void checkAsync(Long userId) {
        log.debug("Running fraud check for userId={}", userId);

        String sql = """
                WITH all_actions AS (
                    SELECT visited_at AS ts FROM profile_visits WHERE visitor_id = :uid
                    UNION ALL
                    SELECT liked_at FROM profile_likes WHERE user_id = :uid
                ),
                first_ts AS (
                    SELECT MIN(ts) AS ts FROM all_actions
                ),
                action_count AS (
                    SELECT COUNT(*) AS cnt
                    FROM (
                        SELECT 1
                        FROM all_actions, first_ts
                        WHERE first_ts.ts IS NOT NULL
                          AND all_actions.ts >= first_ts.ts
                          AND all_actions.ts < first_ts.ts + INTERVAL '10 minutes'
                        LIMIT 100  -- как только нашли 100 — стоп
                    ) t
                )
                UPDATE users u
                SET is_fraud = true
                FROM action_count ac
                WHERE u.id = :uid
                  AND u.is_fraud = false
                  AND ac.cnt >= 100
                """;

        var params = new MapSqlParameterSource("uid", userId);
        int updated = jdbc.update(sql, params);
        if (updated > 0) {
            log.warn("User {} marked as FRAUD", userId);
        }
    }
}
