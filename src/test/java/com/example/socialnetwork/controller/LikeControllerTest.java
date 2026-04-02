package com.example.socialnetwork.controller;

import com.example.socialnetwork.BaseIntegrationTest;
import com.example.socialnetwork.dto.LikeRequest;
import com.example.socialnetwork.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LikeControllerTest extends BaseIntegrationTest {

    @Test
    void like_validRequest_returns201() {
        Long userA = createUser("liker");
        Long userB = createUser("liked");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/user/like",
                new LikeRequest(userA, userB),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_likes WHERE user_id = ? AND liked_user_id = ?",
                Integer.class, userA, userB
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void like_duplicate_isIdempotent() {
        Long userA = createUser("liker2");
        Long userB = createUser("liked2");

        restTemplate.postForEntity("/user/like", new LikeRequest(userA, userB), Void.class);
        restTemplate.postForEntity("/user/like", new LikeRequest(userA, userB), Void.class);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_likes WHERE user_id = ? AND liked_user_id = ?",
                Integer.class, userA, userB
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void like_selfLike_returns400() {
        Long userId = createUser("self_liker");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = restTemplate.postForEntity(
                "/user/like",
                new LikeRequest(userId, userId),
                GlobalExceptionHandler.ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bulkLike_insertsAllRecords_skipsDuplicates() {
        Long userA = createUser("bulk_liker");
        Long userB = createUser("bulk_liked1");
        Long userC = createUser("bulk_liked2");

        List<LikeRequest> bulk = List.of(
                new LikeRequest(userA, userB),
                new LikeRequest(userA, userB),  // duplicate
                new LikeRequest(userA, userC)
        );

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/user/like/bulk", bulk, Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_likes WHERE user_id = ?",
                Integer.class, userA
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    void concurrentLikes_sameUserPair_onlyOneRecordInDb() throws InterruptedException {
        int THREADS = 20;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);

        Long userA = createUser("concurrent_liker");
        Long userB = createUser("concurrent_liked");

        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                restTemplate.postForEntity(
                        "/user/like",
                        new LikeRequest(userA, userB),
                        Void.class
                );
                return null;
            }));
        }

        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_likes WHERE user_id = ? AND liked_user_id = ?",
                Integer.class, userA, userB
        );
        assertThat(count).isEqualTo(1);
    }
}
