package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.ai.client.AiClient;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveAiResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveRequest;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveResponse;
import com.kdh.solvego.domain.ai.dto.AiExplanationRequest;
import com.kdh.solvego.domain.ai.dto.AiExplanationResponse;
import com.kdh.solvego.domain.ai.dto.AiExplanationUsageResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;
import com.kdh.solvego.domain.ai.type.GameEndReason;
import com.kdh.solvego.domain.ai.type.GameResult;
import com.kdh.solvego.domain.ai.type.MoveType;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private static final double AI_RESIGN_WIN_RATE_THRESHOLD = 0.10;

    private final AiClient aiClient;
    private final ExplanationUsageService explanationUsageService;

    public AiService(
            AiClient aiClient,
            ExplanationUsageService explanationUsageService
    ) {
        this.aiClient = aiClient;
        this.explanationUsageService = explanationUsageService;
    }

    public AiRecommendResponse recommend(AiRecommendRequest request) {
        return aiClient.recommend(request);
    }

    public AiAnalyzeResponse analyze(AiAnalyzeRequest request) {
        return aiClient.analyze(request);
    }

    public AiGameNextMoveResponse gameNextMove(AiGameNextMoveRequest request) {
        AiGameNextMoveAiResponse aiResponse = aiClient.gameNextMove(request);

        if (isDoublePass(request, aiResponse)) {
            GameResult result = determineResult(aiResponse.scoreLead());

            return new AiGameNextMoveResponse(
                    aiResponse.moveType(),
                    aiResponse.move(),
                    aiResponse.winRate(),
                    aiResponse.scoreLead(),
                    aiResponse.candidates(),
                    aiResponse.evidenceToken(),
                    true,
                    result,
                    GameEndReason.DOUBLE_PASS
            );
        }

        if (shouldAiResign(aiResponse)) {
            return new AiGameNextMoveResponse(
                    null,
                    null,
                    aiResponse.winRate(),
                    aiResponse.scoreLead(),
                    aiResponse.candidates(),
                    aiResponse.evidenceToken(),
                    true,
                    GameResult.PLAYER_WIN,
                    GameEndReason.AI_RESIGN
            );
        }

        return new AiGameNextMoveResponse(
                aiResponse.moveType(),
                aiResponse.move(),
                aiResponse.winRate(),
                aiResponse.scoreLead(),
                aiResponse.candidates(),
                aiResponse.evidenceToken(),
                false,
                null,
                null
        );
    }

    public AiExplanationResponse explain(
            Long userId,
            AiExplanationRequest request
    ) {
        AiExplanationUsageResponse usage = explanationUsageService.consume(
                userId,
                request.evidenceToken()
        );
        return aiClient.explain(request).withUsage(usage);
    }

    public AiExplanationUsageResponse getExplanationUsage(Long userId) {
        return explanationUsageService.getUsage(userId);
    }

    private boolean isDoublePass(
            AiGameNextMoveRequest request,
            AiGameNextMoveAiResponse aiResponse
    ) {
        if (request.moves() == null || request.moves().isEmpty()) {
            return false;
        }

        AiGameNextMoveRequest.Move lastMove =
                request.moves().get(request.moves().size() - 1);

        return lastMove.moveType() == MoveType.PASS
                && aiResponse.moveType() == MoveType.PASS;
    }

    private boolean shouldAiResign(AiGameNextMoveAiResponse aiResponse) {
        return aiResponse.winRate() <= AI_RESIGN_WIN_RATE_THRESHOLD;
    }

    private GameResult determineResult(double scoreLead) {
        if (scoreLead > 0) {
            return GameResult.AI_WIN;
        }

        if (scoreLead < 0) {
            return GameResult.PLAYER_WIN;
        }

        return GameResult.DRAW;
    }

    public AiStatusResponse status() {
        return aiClient.status();
    }
}
