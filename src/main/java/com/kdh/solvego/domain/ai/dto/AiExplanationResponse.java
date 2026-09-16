package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.Player;

import java.util.List;

public record AiExplanationResponse(
        String source,
        Player perspective,
        List<AiGameCandidate> candidates,
        Explanation explanation,
        AiExplanationUsageResponse usage
) {
    public AiExplanationResponse(
            String source,
            Player perspective,
            List<AiGameCandidate> candidates,
            Explanation explanation
    ) {
        this(source, perspective, candidates, explanation, null);
    }

    public AiExplanationResponse withUsage(AiExplanationUsageResponse usage) {
        return new AiExplanationResponse(
                source,
                perspective,
                candidates,
                explanation,
                usage
        );
    }

    public record Explanation(
            String summary,
            String comparison,
            String pvExplanation,
            String limitation,
            List<String> evidenceRefs
    ) {
    }
}
