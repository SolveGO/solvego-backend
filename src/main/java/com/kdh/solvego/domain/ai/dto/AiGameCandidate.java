package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.common.vo.Position;

import java.util.List;

public record AiGameCandidate(
        String id,
        int rank,
        MoveType moveType,
        Position move,
        double winRate,
        double scoreLead,
        int visits,
        List<Position> pv
) {
}
