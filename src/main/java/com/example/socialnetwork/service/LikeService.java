package com.example.socialnetwork.service;

import com.example.socialnetwork.exception.SelfInteractionException;
import com.example.socialnetwork.model.ProfileLike;
import com.example.socialnetwork.repository.ProfileLikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class LikeService {

    private final ProfileLikeRepository likeRepository;
    private final FraudService fraudService;

    @Transactional
    public void like(Long userId, Long likedUserId) {
        if (userId.equals(likedUserId)) {
            throw new SelfInteractionException("Cannot like your own profile");
        }

        ProfileLike like = ProfileLike.builder()
                .userId(userId)
                .likedUserId(likedUserId)
                .likedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        // ON CONFLICT DO NOTHING handles duplicates at DB level
        likeRepository.save(like);
        log.trace("Saved like from {} to {}", userId, likedUserId);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        fraudService.checkAsync(userId);
                    }
                }
        );
    }

    @Transactional
    public void bulkLike(List<ProfileLike> likes) {
        if (likes.isEmpty()) return;
        likeRepository.batchInsert(likes);

        likes.stream()
                .map(ProfileLike::getUserId)
                .distinct()
                .forEach(fraudService::checkAsync);

        log.trace("Saved bulk likes. Size: {}", likes.size());
    }
}