package com.example.socialnetwork;

import com.example.socialnetwork.dto.CreateUserRequest;
import com.example.socialnetwork.dto.CreateUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        postgres.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE TABLE profile_likes, profile_visits, users RESTART IDENTITY CASCADE");
    }

    protected Long createUser(String username) {
        CreateUserRequest req = new CreateUserRequest(
                username,
                "Full Name",
                25,
                Map.of("city", "Paris")
        );
        ResponseEntity<CreateUserResponse> response = restTemplate.postForEntity(
                "/user", req, CreateUserResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody()).id();
    }
}