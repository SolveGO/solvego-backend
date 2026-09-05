package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AiRecommendRequest(

        @NotNull
        List<@Valid Position> blackStones,

        @NotNull
        List<@Valid Position> whiteStones,

        @NotNull
        PlayerColor nextPlayer

) {
}