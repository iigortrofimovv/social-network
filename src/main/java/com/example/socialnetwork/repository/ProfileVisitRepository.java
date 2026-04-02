package com.example.socialnetwork.repository;

import com.example.socialnetwork.model.ProfileVisit;
import com.example.socialnetwork.repository.projection.VisitorView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class ProfileVisitRepository {

    private final JdbcTemplate jdbc;

    public void save(ProfileVisit visit) {
        jdbc.update("""
                        INSERT INTO profile_visits (visitor_id, visited_id, visited_at)
                        VALUES (?, ?, ?)
                        """,
                visit.getVisitorId(),
                visit.getVisitedId(),
                visit.getVisitedAt()
        );
    }

    public void batchInsert(List<ProfileVisit> visits) {
        List<ProfileVisit> safeList = List.copyOf(visits);
        jdbc.batchUpdate("""
                        INSERT INTO profile_visits (visitor_id, visited_id, visited_at)
                        VALUES (?, ?, ?)
                        """,
                safeList,
                safeList.size(),
                (ps, v) -> {
                    ps.setLong(1, v.getVisitorId());
                    ps.setLong(2, v.getVisitedId());
                    ps.setObject(3, v.getVisitedAt());
                }
        );
    }

    public List<VisitorView> findVisitors(Long userId, int limit, int offset) {
        return jdbc.query("""
                        SELECT visitor_id,
                               MAX(visited_at) AS last_visit
                        FROM profile_visits
                        WHERE visited_id = ?
                        GROUP BY visitor_id
                        ORDER BY last_visit DESC
                        LIMIT ? OFFSET ?
                        """,
                (rs, row) -> new VisitorView(
                        rs.getLong("visitor_id"),
                        rs.getObject("last_visit", OffsetDateTime.class)
                ),
                userId, limit, offset
        );
    }
}