package com.kdh.solvego.domain.user.dto;

import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;

import java.time.LocalDateTime;
import java.util.List;

public record MyPageResponse(
        String username,
        LocalDateTime joinedAt,
        long registeredProblemCount,
        long solvedProblemCount,
        long wrongProblemCount,
        SubscriptionPlan plan,
        List<MyPageProblemResponse> problems
) {
}
