package com.example.socialnetwork.controller;

import com.example.socialnetwork.dto.VisitRequest;
import com.example.socialnetwork.model.ProfileVisit;
import com.example.socialnetwork.repository.projection.VisitorView;
import com.example.socialnetwork.service.VisitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class VisitController {

    private final VisitService visitService;

    @PostMapping("/visit")
    public ResponseEntity<Void> visit(@RequestBody @Valid VisitRequest req) {
        visitService.visit(req.visitorId(), req.visitedId());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/visit/bulk")
    public ResponseEntity<Void> bulkVisit(
            @RequestBody @Size(max = 1000) List<@Valid VisitRequest> reqs
    ) {
        List<ProfileVisit> visits = reqs.stream()
                .map(ProfileVisit::from)
                .toList();

        visitService.bulkVisit(visits);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{userId}/visitors")
    public List<VisitorView> getVisitors(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        return visitService.getVisitors(userId, page, size);
    }
}
