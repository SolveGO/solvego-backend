package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.Player;

import java.util.List;

public record AiExplanationResponse(
        String source,
        Player perspective,
        List<AiGameCandidate> candidates,
        Explanation explanation
) {
    public record Explanation(
            String summary,
            String comparison,
            String pvExplanation,
            String limitation,
            List<String> evidenceRefs
    ) {
    }
}
