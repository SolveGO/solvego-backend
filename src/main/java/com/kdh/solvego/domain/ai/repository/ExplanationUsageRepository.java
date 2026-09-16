package com.kdh.solvego.domain.ai.repository;

import com.kdh.solvego.domain.ai.exception.ExplanationDailyLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class ExplanationUsageRepository {
    private static final DefaultRedisScript<List> CONSUME_SCRIPT =
            new DefaultRedisScript<>("""
                    local already_used = redis.call('HEXISTS', KEYS[1], ARGV[1])
                    local used = redis.call('HLEN', KEYS[1])
                    local limit = tonumber(ARGV[2])
                    if already_used == 1 then
                        return {used, limit - used}
                    end
                    if used >= limit then
                        return {-1, 0}
                    end
                    redis.call('HSET', KEYS[1], ARGV[1], '1')
                    redis.call('EXPIREAT', KEYS[1], ARGV[3])
                    return {used + 1, limit - used - 1}
                    """, List.class);

    private final StringRedisTemplate redis;

    public ExplanationUsageRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public UsageCount consume(
            Long userId,
            LocalDate date,
            String evidenceHash,
            int dailyLimit,
            Instant resetsAt
    ) {
        List<?> result = redis.execute(
                CONSUME_SCRIPT,
                List.of(key(userId, date)),
                evidenceHash,
                String.valueOf(dailyLimit),
                String.valueOf(resetsAt.getEpochSecond())
        );
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Cannot record explanation usage");
        }
        int usedCount = ((Number) result.get(0)).intValue();
        if (usedCount < 0) {
            throw new ExplanationDailyLimitExceededException();
        }
        return new UsageCount(usedCount, ((Number) result.get(1)).intValue());
    }

    public int count(Long userId, LocalDate date) {
        Long count = redis.opsForHash().size(key(userId, date));
        return count == null ? 0 : count.intValue();
    }

    private String key(Long userId, LocalDate date) {
        return "solvego:ai:explanation-usage:" + userId + ":" + date;
    }

    public record UsageCount(int usedCount, int remainingCount) {
    }
}
