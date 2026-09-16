package com.kdh.solvego.domain.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MyPageResponse(
        String username,
        LocalDateTime joinedAt,
        long registeredProblemCount,
        long solvedProblemCount,
        long wrongProblemCount,
        List<MyPageProblemResponse> problems
) {
}
