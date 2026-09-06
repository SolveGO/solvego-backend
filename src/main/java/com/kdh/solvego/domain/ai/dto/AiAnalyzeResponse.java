package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.common.vo.Position;

import java.util.List;

public record AiAnalyzeResponse(
        Position bestMove,
        Position selectedMove,
        double bestWinRate,
        double selectedWinRate,
        double winRateLoss,
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