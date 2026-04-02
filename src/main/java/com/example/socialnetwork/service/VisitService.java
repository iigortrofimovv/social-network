package com.example.socialnetwork.service;

import com.example.socialnetwork.exception.SelfInteractionException;
import com.example.socialnetwork.model.ProfileVisit;
import com.example.socialnetwork.repository.ProfileVisitRepository;
import com.example.socialnetwork.repository.projection.VisitorView;
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
public class VisitService {

    private final ProfileVisitRepository visitRepository;
    private final FraudService fraudService;

    @Transactional
    public void visit(Long visitorId, Long visitedId) {
        if (visitorId.equals(visitedId)) {
            throw new SelfInteractionException("Cannot visit your own profile");
        }
        ProfileVisit visit = ProfileVisit.builder()
                .visitorId(visitorId)
                .visitedId(visitedId)
                .visitedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        visitRepository.save(visit);
        log.trace("Saved visit from {} to {}", visitorId, visitedId);

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        fraudService.checkAsync(visitorId);
                    }
                }
        );
    }

    @Transactional
    public void bulkVisit(List<ProfileVisit> visits) {
        if (visits.isEmpty()) {
            return;
        }
        visitRepository.batchInsert(visits);

        visits.stream()
                .map(ProfileVisit::getVisitorId)
                .distinct()
                .forEach(fraudService::checkAsync);

        log.trace("Saved bulk visits. Size: {}", visits.size());
    }

    public List<VisitorView> getVisitors(Long userId, int page, int size) {
        int offset = page * size;
        return visitRepository.findVisitors(userId, size, offset);
    }
}
