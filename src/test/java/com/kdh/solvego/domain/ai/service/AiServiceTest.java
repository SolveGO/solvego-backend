package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.ai.client.AiClient;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveAiResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveRequest;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveResponse;
import com.kdh.solvego.domain.ai.dto.AiGameCandidate;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;
import com.kdh.solvego.domain.ai.type.GameEndReason;
import com.kdh.solvego.domain.ai.type.GameResult;
import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.ai.type.Player;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private AiClient aiClient;

    @InjectMocks
    private AiService aiService;

    @Test
    @DisplayName("AI 추천 요청에 성공하면 AiClient의 추천 결과를 반환한다")
    void recommend_success() {
        // given
        AiRecommendRequest request = new AiRecommendRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK
        );

        AiRecommendResponse expectedResponse =
                new AiRecommendResponse(
                        new Position(15, 3),
                        0.48,
                        2.5,
                        List.of(
                                new AiRecommendResponse.Candidate(
                                        new Position(15, 3),
                                        0.48,
                                        2.5,
                                        10,
                                        List.of(
                                                new Position(15, 3),
                                                new Position(3, 15)
                                        )
                                )
                        )
                );

        when(aiClient.recommend(request))
                .thenReturn(expectedResponse);

        // when
        AiRecommendResponse response =
                aiService.recommend(request);

        // then
        assertThat(response)
                .isEqualTo(expectedResponse);

        verify(aiClient).recommend(request);
    }

    @Test
    @DisplayName("AI 수 분석 요청에 성공하면 AiClient의 분석 결과를 반환한다")
    void analyze_success() {
        // given
        AiAnalyzeRequest request = new AiAnalyzeRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        AiAnalyzeResponse expectedResponse =
                new AiAnalyzeResponse(
                        new Position(15, 3),
                        new Position(10, 10),
                        0.48,
                        0.41,
                        0.07,
                        2.5,
                        List.of(
                                new AiAnalyzeResponse.Candidate(
                                        new Position(15, 3),
                                        0.48,
                                        2.5,
                                        10,
                                        List.of(
                                                new Position(15, 3),
                                                new Position(3, 15)
                                        )
                                )
                        )
                );

        when(aiClient.analyze(request))
                .thenReturn(expectedResponse);

        // when
        AiAnalyzeResponse response =
                aiService.analyze(request);

        // then
        assertThat(response)
                .isEqualTo(expectedResponse);

        verify(aiClient).analyze(request);
    }

    @Test
    @DisplayName("AI 서버 상태 조회에 성공하면 AiClient의 상태 조회 결과를 반환한다")
    void status_success() {
        // given
        AiStatusResponse expectedResponse =
                new AiStatusResponse("ONLINE");

        when(aiClient.status())
                .thenReturn(expectedResponse);

        // when
        AiStatusResponse response =
                aiService.status();

        // then
        assertThat(response)
                .isEqualTo(expectedResponse);

        verify(aiClient).status();
    }

    @Test
    @DisplayName("일반적인 AI 착수이면 대국을 계속한다")
    void gameNextMove_continue() {
        // given
        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        Player.BLACK,
                                        MoveType.PLAY,
                                        new Position(3, 15)
                                )
                        )
                );

        Position aiMove = new Position(4, 3);
        AiGameCandidate candidate = new AiGameCandidate(
                "c1", 1, MoveType.PLAY, aiMove, 0.99, 13.05,
                5, List.of(aiMove)
        );

        AiGameNextMoveAiResponse aiResponse =
                new AiGameNextMoveAiResponse(
                        MoveType.PLAY,
                        aiMove,
                        0.99,
                        13.05,
                        List.of(candidate),
                        "signed-evidence"
                );

        when(aiClient.gameNextMove(request))
                .thenReturn(aiResponse);

        // when
        AiGameNextMoveResponse response =
                aiService.gameNextMove(request);

        // then
        assertThat(response.moveType())
                .isEqualTo(MoveType.PLAY);

        assertThat(response.move())
                .isEqualTo(aiMove);

        assertThat(response.winRate())
                .isEqualTo(0.99);

        assertThat(response.scoreLead())
                .isEqualTo(13.05);

        assertThat(response.candidates()).containsExactly(candidate);
        assertThat(response.evidenceToken()).isEqualTo("signed-evidence");

        assertThat(response.gameEnded())
                .isFalse();

        assertThat(response.result())
                .isNull();

        assertThat(response.endReason())
                .isNull();

        verify(aiClient).gameNextMove(request);
    }

    @Test
    @DisplayName("사용자와 AI가 연속으로 PASS하면 AI 승리로 대국을 종료한다")
    void gameNextMove_doublePass_aiWin() {
        // given
        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        Player.BLACK,
                                        MoveType.PASS,
                                        null
                                )
                        )
                );

        AiGameNextMoveAiResponse aiResponse =
                new AiGameNextMoveAiResponse(
                        MoveType.PASS,
                        null,
                        0.80,
                        5.5
                );

        when(aiClient.gameNextMove(request))
                .thenReturn(aiResponse);

        // when
        AiGameNextMoveResponse response =
                aiService.gameNextMove(request);

        // then
        assertThat(response.gameEnded())
                .isTrue();

        assertThat(response.result())
                .isEqualTo(GameResult.AI_WIN);

        assertThat(response.endReason())
                .isEqualTo(GameEndReason.DOUBLE_PASS);

        assertThat(response.moveType())
                .isEqualTo(MoveType.PASS);

        assertThat(response.move())
                .isNull();

        verify(aiClient).gameNextMove(request);
    }

    @Test
    @DisplayName("연속 PASS 종료 시 scoreLead가 음수이면 사용자 승리이다")
    void gameNextMove_doublePass_playerWin() {
        // given
        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        Player.BLACK,
                                        MoveType.PASS,
                                        null
                                )
                        )
                );

        AiGameNextMoveAiResponse aiResponse =
                new AiGameNextMoveAiResponse(
                        MoveType.PASS,
                        null,
                        0.20,
                        -7.5
                );

        when(aiClient.gameNextMove(request))
                .thenReturn(aiResponse);

        // when
        AiGameNextMoveResponse response =
                aiService.gameNextMove(request);

        // then
        assertThat(response.gameEnded())
                .isTrue();

        assertThat(response.result())
                .isEqualTo(GameResult.PLAYER_WIN);

        assertThat(response.endReason())
                .isEqualTo(GameEndReason.DOUBLE_PASS);

        verify(aiClient).gameNextMove(request);
    }

    @Test
    @DisplayName("연속 PASS 종료 시 scoreLead가 0이면 무승부이다")
    void gameNextMove_doublePass_draw() {
        // given
        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        Player.BLACK,
                                        MoveType.PASS,
                                        null
                                )
                        )
                );

        AiGameNextMoveAiResponse aiResponse =
                new AiGameNextMoveAiResponse(
                        MoveType.PASS,
                        null,
                        0.50,
                        0.0
                );

        when(aiClient.gameNextMove(request))
                .thenReturn(aiResponse);

        // when
        AiGameNextMoveResponse response =
                aiService.gameNextMove(request);

        // then
        assertThat(response.gameEnded())
                .isTrue();

        assertThat(response.result())
                .isEqualTo(GameResult.DRAW);

        assertThat(response.endReason())
                .isEqualTo(GameEndReason.DOUBLE_PASS);

        verify(aiClient).gameNextMove(request);
    }

    @Test
    @DisplayName("AI 승률이 10% 이하이면 AI가 기권하고 사용자가 승리한다")
    void gameNextMove_aiResign() {
        // given
        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        Player.BLACK,
                                        MoveType.PLAY,
                                        new Position(3, 15)
                                )
                        )
                );

        AiGameNextMoveAiResponse aiResponse =
                new AiGameNextMoveAiResponse(
                        MoveType.PLAY,
                        new Position(4, 3),
                        0.10,
                        -15.0
                );

        when(aiClient.gameNextMove(request))
                .thenReturn(aiResponse);

        // when
        AiGameNextMoveResponse response =
                aiService.gameNextMove(request);

        // then
        assertThat(response.gameEnded())
                .isTrue();

        assertThat(response.result())
                .isEqualTo(GameResult.PLAYER_WIN);

        assertThat(response.endReason())
                .isEqualTo(GameEndReason.AI_RESIGN);

        assertThat(response.moveType())
                .isNull();

        assertThat(response.move())
                .isNull();

        verify(aiClient).gameNextMove(request);
    }
}
