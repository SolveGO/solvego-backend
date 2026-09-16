package com.kdh.solvego.domain.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExplanationUsageLimitPolicy {
    private final int freeDailyLimit;

    public ExplanationUsageLimitPolicy(
            @Value("${ai.explanation.free-daily-limit}") int freeDailyLimit
    ) {
        if (freeDailyLimit < 1) {
            throw new IllegalArgumentException(
                    "AI explanation daily limit must be positive"
            );
        }
        this.freeDailyLimit = freeDailyLimit;
    }

    public int dailyLimitFor(Long userId) {
        return freeDailyLimit;
    }
}
