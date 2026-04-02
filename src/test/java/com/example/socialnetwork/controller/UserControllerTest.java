package com.example.socialnetwork.controller;

import com.example.socialnetwork.BaseIntegrationTest;
import com.example.socialnetwork.dto.CreateUserRequest;
import com.example.socialnetwork.dto.CreateUserResponse;
import com.example.socialnetwork.dto.UserResponse;
import com.example.socialnetwork.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest extends BaseIntegrationTest {

    @Test
    void createUser_validRequest_returns201AndId() {
        CreateUserRequest req = new CreateUserRequest(
                "john_doe", "John Doe", 30, Map.of("city", "SPB")
        );

        ResponseEntity<CreateUserResponse> response = restTemplate.postForEntity(
                "/user", req, CreateUserResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isPositive();
    }

    @Test
    void createUser_duplicateUsername_returns409() {
        createUser("same_name");

        CreateUserRequest duplicate = new CreateUserRequest(
                "same_name", "Full Name", 20, Map.of()
        );
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = restTemplate.postForEntity(
                "/user", duplicate, GlobalExceptionHandler.ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createUser_invalidAge_returns400() {
        CreateUserRequest req = new CreateUserRequest(
                "user1", "Name", 200, Map.of()  // age > 120
        );

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = restTemplate.postForEntity(
                "/user", req, GlobalExceptionHandler.ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("age");
    }

    @Test
    void createUser_blankUsername_returns400() {
        CreateUserRequest req = new CreateUserRequest(
                "", "Name", 25, Map.of()
        );

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = restTemplate.postForEntity(
                "/user", req, GlobalExceptionHandler.ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getUser_existingUser_returnsUserWithMetadata() {
        Long id = createUser("meta_user");

        ResponseEntity<UserResponse> response = restTemplate.getForEntity(
                "/user/{id}", UserResponse.class, id
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().username()).isEqualTo("meta_user");
        assertThat(response.getBody().metadata()).containsKey("city");
    }

    @Test
    void getUser_notExisting_returns404() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = restTemplate.getForEntity(
                "/user/99999", GlobalExceptionHandler.ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}