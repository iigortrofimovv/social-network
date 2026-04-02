package com.example.socialnetwork;

import com.example.socialnetwork.dto.VisitRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FraudDetectionTest extends BaseIntegrationTest {

    @Test
    void fraudCheck_100ActionsWithin10Minutes_marksUserAsFraud() {
        Long fraudUser = createUser("fraud_user");

        // Crate 50 users for visits and likes
        List<Long> targets = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            targets.add(createUser("target_" + i));
        }

        // 50 visits + 50 likes = 100 actions — insert directly in DB to avoid 100 HTTP requests and time control
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Long target : targets) {
            jdbc.update("""
                    INSERT INTO profile_visits (visitor_id, visited_id, visited_at)
                    VALUES (?, ?, ?)
                    """, fraudUser, target, now);
            jdbc.update("""
                    INSERT INTO profile_likes (user_id, liked_user_id, liked_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, fraudUser, target, now.plusSeconds(1));
        }

        // Check with one more visit
        Long extraTarget = createUser("extra_target");
        restTemplate.postForEntity(
                "/user/visit",
                new VisitRequest(fraudUser, extraTarget),
                Void.class
        );

        // Wait the end of @Async task
        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Boolean isFraud = jdbc.queryForObject(
                            "SELECT is_fraud FROM users WHERE id = ?",
                            Boolean.class, fraudUser
                    );
                    assertThat(isFraud).isTrue();
                });
    }

    @Test
    void fraudCheck_100ActionsButSpreadOverMoreThan10Minutes_doesNotMarkFraud() {
        Long normalUser = createUser("normal_user");
        List<Long> targets = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            targets.add(createUser("spread_target_" + i));
        }

        // First 50 actions — time ago
        OffsetDateTime longAgo = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30);
        // Second 50 actions — now
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (int i = 0; i < 50; i++) {
            jdbc.update("INSERT INTO profile_visits (visitor_id, visited_id, visited_at) VALUES (?, ?, ?)",
                    normalUser, targets.get(i), i < 25 ? longAgo : now);
            jdbc.update("INSERT INTO profile_likes (user_id, liked_user_id, liked_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    normalUser, targets.get(i), i < 25 ? longAgo.plusSeconds(1) : now.plusSeconds(1));
        }

        Long extraTarget = createUser("extra_spread_target");
        restTemplate.postForEntity("/user/visit", new VisitRequest(normalUser, extraTarget), Void.class);

        // Wait and check that fraud is not set
        await().during(3, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Boolean isFraud = jdbc.queryForObject(
                            "SELECT is_fraud FROM users WHERE id = ?",
                            Boolean.class, normalUser
                    );
                    assertThat(isFraud).isFalse();
                });
    }

    @Test
    void fraudCheck_alreadyFraudUser_staysFraud() {
        Long fraudUser = createUser("already_fraud");
        jdbc.update("UPDATE users SET is_fraud = true WHERE id = ?", fraudUser);
        Long target = createUser("target_for_fraud");

        restTemplate.postForEntity("/user/visit", new VisitRequest(fraudUser, target), Void.class);

        Boolean isFraud = jdbc.queryForObject(
                "SELECT is_fraud FROM users WHERE id = ?", Boolean.class, fraudUser
        );
        assertThat(isFraud).isTrue();
    }
}