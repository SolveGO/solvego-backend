package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.ai.type.Player;
import com.kdh.solvego.domain.common.vo.Position;

import java.util.List;

public record AiGameNextMoveRequest(
        List<Move> moves
) {

    public record Move(
            Player player,
            MoveType moveType,
            Position position
    ) {
    }
}