package com.kdh.solvego.domain.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RefreshSessionRepository {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public record Session(long userId, long createdAt, long expiresAt) {}

    public RefreshSessionRepository(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void save(String key, Session session, Duration ttl) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(session), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize refresh session", e);
        }
    }

    public Optional<Session> find(String key) {
        String json = redis.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(json, Session.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read refresh session", e);
        }
    }

    public void delete(String key) { redis.delete(key); }
}
