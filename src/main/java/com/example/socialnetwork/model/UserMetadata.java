package com.example.socialnetwork.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;


public record UserMetadata(Map<String, Object> fields) {

    public UserMetadata {
        Objects.requireNonNull(fields, "fields must not be null");
        fields = Map.copyOf(fields);
    }

    public static UserMetadata empty() {
        return new UserMetadata(Map.of());
    }

    public static UserMetadata of(Map<String, Object> fields) {
        return new UserMetadata(fields);
    }

    public String toJson(ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize metadata", e);
        }
    }

    public static UserMetadata fromJson(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return empty();
        try {
            Map<String, Object> parsed = mapper.readValue(
                    json, new TypeReference<>() {
                    }
            );
            return new UserMetadata(parsed);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize metadata", e);
        }
    }
}