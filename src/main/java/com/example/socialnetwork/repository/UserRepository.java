package com.example.socialnetwork.repository;

import com.example.socialnetwork.model.User;
import com.example.socialnetwork.model.UserMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<User> rowMapper;

    public UserRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, row) -> User.builder()
                .id(rs.getLong("id"))
                .username(rs.getString("username"))
                .fullName(rs.getString("full_name"))
                .age(rs.getInt("age"))
                .metadata(UserMetadata.fromJson(rs.getString("metadata"), objectMapper))
                .isFraud(rs.getBoolean("is_fraud"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .build();
    }

    public Long save(User user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO users (username, full_name, age, metadata, is_fraud, created_at)
                    VALUES (?, ?, ?, ?::jsonb, false, NOW())
                    """, new String[]{"id"});
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setInt(3, user.getAge());
            ps.setString(4, user.getMetadata().toJson(objectMapper));
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public Optional<User> findById(Long id) {
        List<User> result = jdbc.query(
                "SELECT * FROM users WHERE id = ?", rowMapper, id
        );
        return result.stream().findFirst();
    }
}