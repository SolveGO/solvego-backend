package com.kdh.solvego.domain.auth.service;

import com.kdh.solvego.domain.auth.exception.InvalidRefreshException;
import com.kdh.solvego.domain.auth.repository.RefreshSessionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshSessionService {
    public static final Duration LIFETIME = Duration.ofDays(7);
    private final SecureRandom random = new SecureRandom();
    private final RefreshSessionRepository repository;

    public RefreshSessionService(RefreshSessionRepository repository) {
        this.repository = repository;
    }

    public String create(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        repository.save(key(token), new RefreshSessionRepository.Session(
                userId, now.getEpochSecond(), now.plus(LIFETIME).getEpochSecond()), LIFETIME);
        return token;
    }

    public long validate(String token) {
        if (!wellFormed(token)) throw new InvalidRefreshException();
        var session = repository.find(key(token)).orElseThrow(InvalidRefreshException::new);
        if (session.expiresAt() <= Instant.now().getEpochSecond()) {
            throw new InvalidRefreshException();
        }
        // Read only: refresh does not rotate the token or extend the TTL.
        return session.userId();
    }

    public void revoke(String token) {
        if (wellFormed(token)) repository.delete(key(token));
    }

    private boolean wellFormed(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private String key(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return "solvego:auth:refresh:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
