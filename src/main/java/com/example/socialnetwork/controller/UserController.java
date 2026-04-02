package com.example.socialnetwork.controller;

import com.example.socialnetwork.dto.CreateUserRequest;
import com.example.socialnetwork.dto.CreateUserResponse;
import com.example.socialnetwork.dto.UserResponse;
import com.example.socialnetwork.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(
            @RequestBody @Valid CreateUserRequest req
    ) {
        Long id = userService.createUser(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateUserResponse(id));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(UserResponse.from(userService.getUser(userId)));
    }
}
