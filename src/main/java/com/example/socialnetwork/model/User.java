package com.example.socialnetwork.model;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @EqualsAndHashCode.Include
    private Long id;

    private String username;
    private String fullName;
    private Integer age;
    private UserMetadata metadata;
    private boolean isFraud;

    private OffsetDateTime createdAt;
}