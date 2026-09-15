package com.kdh.solvego.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiExplanationRequest(
        @NotBlank String evidenceToken
) {
}
