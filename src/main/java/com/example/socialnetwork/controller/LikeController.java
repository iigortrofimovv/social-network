package com.example.socialnetwork.controller;

import com.example.socialnetwork.dto.LikeRequest;
import com.example.socialnetwork.model.ProfileLike;
import com.example.socialnetwork.service.LikeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/like")
public class LikeController {

    private final LikeService likeService;

    @PostMapping
    public ResponseEntity<Void> like(@RequestBody @Valid LikeRequest req) {
        likeService.like(req.userId(), req.likedUserId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<Void> bulkLike(
            @RequestBody @Size(max = 1000) List<@Valid LikeRequest> reqs
    ) {
        List<ProfileLike> likes = reqs.stream()
                .map(ProfileLike::from)
                .toList();

        likeService.bulkLike(likes);
        return ResponseEntity.accepted().build();
    }
}