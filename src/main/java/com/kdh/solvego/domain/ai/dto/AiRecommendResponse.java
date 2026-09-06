package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.common.vo.Position;

import java.util.List;

public record AiRecommendResponse(
        Position bestMove,
        double bestWinRate,
        double scoreLead,
        List<Candidate> candidates
) {

    public record Candidate(
            Position move,
            double winRate,
            double scoreLead,
            int visits,
            List<Position> pv
    ) {
    }
}