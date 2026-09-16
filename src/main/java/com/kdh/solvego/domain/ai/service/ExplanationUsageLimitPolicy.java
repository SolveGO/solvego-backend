package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.domain.user.type.SubscriptionPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class ExplanationUsageLimitPolicy {
    private final UserRepository userRepository;
    private final int freeDailyLimit;
    private final int proDailyLimit;
    private final Clock clock;

    @Autowired
    public ExplanationUsageLimitPolicy(
            UserRepository userRepository,
            @Value("${ai.explanation.free-daily-limit}") int freeDailyLimit,
            @Value("${ai.explanation.pro-daily-limit}") int proDailyLimit
    ) {
        this(
                userRepository,
                freeDailyLimit,
                proDailyLimit,
                Clock.systemUTC()
        );
    }

    ExplanationUsageLimitPolicy(
            UserRepository userRepository,
            int freeDailyLimit,
            int proDailyLimit,
            Clock clock
    ) {
        if (freeDailyLimit < 1 || proDailyLimit < freeDailyLimit) {
            throw new IllegalArgumentException(
                    "AI explanation daily limits are invalid"
            );
        }
        this.userRepository = userRepository;
        this.freeDailyLimit = freeDailyLimit;
        this.proDailyLimit = proDailyLimit;
        this.clock = clock;
    }

    public int dailyLimitFor(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return user.getCurrentPlan(Instant.now(clock)) == SubscriptionPlan.PRO
                ? proDailyLimit
                : freeDailyLimit;
    }
}
