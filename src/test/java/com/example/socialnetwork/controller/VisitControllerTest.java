package com.example.socialnetwork.controller;

import com.example.socialnetwork.BaseIntegrationTest;
import com.example.socialnetwork.dto.VisitRequest;
import com.example.socialnetwork.exception.GlobalExceptionHandler;
import com.example.socialnetwork.repository.projection.VisitorView;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitControllerTest extends BaseIntegrationTest {

    @Test
    void visit_validRequest_returns201AndRecordsVisit() {
        Long userA = createUser("visitor");
        Long userB = createUser("visited");

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/user/visit",
                new VisitRequest(userA, userB),
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_visits WHERE visitor_id = ? AND visited_id = ?",
                Integer.class, userA, userB
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void visit_selfVisit_returns400() {
        Long userId = createUser("self_visitor");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = restTemplate.postForEntity(
                "/user/visit",
                new VisitRequest(userId, userId),
                GlobalExceptionHandler.ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("Cannot visit your own profile");
    }

    @Test
    void visit_allowsMultipleVisits_recordsEach() {
        Long userA = createUser("multi_visitor");
        Long userB = createUser("multi_visited");

        restTemplate.postForEntity("/user/visit", new VisitRequest(userA, userB), Void.class);
        restTemplate.postForEntity("/user/visit", new VisitRequest(userA, userB), Void.class);
        restTemplate.postForEntity("/user/visit", new VisitRequest(userA, userB), Void.class);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_visits WHERE visitor_id = ?",
                Integer.class, userA
        );
        assertThat(count).isEqualTo(3);
    }

    @Test
    void getVisitors_returnsVisitorsSortedByLastVisitDesc() {
        Long owner = createUser("owner");
        Long visitorA = createUser("visitorA");
        Long visitorB = createUser("visitorB");

        // visitorA visits earlier
        restTemplate.postForEntity("/user/visit", new VisitRequest(visitorA, owner), Void.class);
        // visitorB visits later
        restTemplate.postForEntity("/user/visit", new VisitRequest(visitorB, owner), Void.class);

        ResponseEntity<VisitorView[]> response = restTemplate.getForEntity(
                "/user/{userId}/visitors", VisitorView[].class, owner
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);

        assertThat(response.getBody()[0].visitorId()).isEqualTo(visitorB);
        assertThat(response.getBody()[1].visitorId()).isEqualTo(visitorA);
    }

    @Test
    void bulkVisit_insertsAllRecords() {
        Long userA = createUser("bulk_visitor");
        Long userB = createUser("bulk_target1");
        Long userC = createUser("bulk_target2");

        List<VisitRequest> bulk = List.of(
                new VisitRequest(userA, userB),
                new VisitRequest(userA, userC)
        );

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/user/visit/bulk", bulk, Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_visits WHERE visitor_id = ?",
                Integer.class, userA
        );
        assertThat(count).isEqualTo(2);
    }

    @Test
    void getVisitors_pagination_returnsCorrectPage() {
        Long owner = createUser("paged_owner");

        for (int i = 0; i < 5; i++) {
            Long visitor = createUser("paged_visitor_" + i);
            restTemplate.postForEntity("/user/visit", new VisitRequest(visitor, owner), Void.class);
        }

        ResponseEntity<VisitorView[]> page0 = restTemplate.getForEntity(
                "/user/{userId}/visitors?page=0&size=3", VisitorView[].class, owner
        );
        ResponseEntity<VisitorView[]> page1 = restTemplate.getForEntity(
                "/user/{userId}/visitors?page=1&size=3", VisitorView[].class, owner
        );

        assertThat(page0.getBody()).hasSize(3);
        assertThat(page1.getBody()).hasSize(2);
    }

    @Test
    void concurrentFraudCheck_onlyOneUpdateExecuted() throws InterruptedException {
        Long fraudUser = createUser("concurrent_fraud_user");
        List<Long> targets = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            targets.add(createUser("cf_target_" + i));
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Long target : targets) {
            jdbc.update(
                    "INSERT INTO profile_visits (visitor_id, visited_id, visited_at) VALUES (?, ?, ?)",
                    fraudUser, target, now
            );
            jdbc.update(
                    "INSERT INTO profile_likes (user_id, liked_user_id, liked_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    fraudUser, target, now.plusSeconds(1)
            );
        }

        int threads = 10;
        ExecutorService fraudExecutor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            Long target = createUser("cf_extra_target_" + i);
            futures.add(fraudExecutor.submit(() -> {
                barrier.await();
                restTemplate.postForEntity(
                        "/user/visit",
                        new VisitRequest(fraudUser, target),
                        Void.class
                );
                return null;
            }));
        }

        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(10, TimeUnit.SECONDS));
        }
        fraudExecutor.shutdown();
        assertTrue(fraudExecutor.awaitTermination(15, TimeUnit.SECONDS));

        await().atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(jdbc.queryForObject(
                                "SELECT is_fraud FROM users WHERE id = ?",
                                Boolean.class, fraudUser
                        )).isTrue()
                );
    }

    @Test
    void concurrentVisits_differentUsers_allRecorded() throws InterruptedException {
        int THREADS = 20;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);

        Long visited = createUser("popular_user");
        List<Long> visitors = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            visitors.add(createUser("parallel_visitor_" + i));
        }

        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Future<?>> futures = visitors.stream()
                .map(visitor -> executor.submit(() -> {
                    barrier.await();
                    restTemplate.postForEntity(
                            "/user/visit",
                            new VisitRequest(visitor, visited),
                            Void.class
                    );
                    return null;
                }))
                .collect(Collectors.toList());

        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_visits WHERE visited_id = ?",
                Integer.class, visited
        );
        assertThat(count).isEqualTo(THREADS);
    }
}