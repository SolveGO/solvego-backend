package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.ai.client.AiClient;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveRequest;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveResponse;

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
    @DisplayName("AI 대국 다음 수 요청에 성공하면 AiClient의 결과를 반환한다")
    void gameNextMove_success() {
        // given
        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        AiGameNextMoveRequest.Player.BLACK,
                                        new Position(3, 15)
                                ),
                                new AiGameNextMoveRequest.Move(
                                        AiGameNextMoveRequest.Player.WHITE,
                                        null
                                )
                        )
                );

        AiGameNextMoveResponse expectedResponse =
                new AiGameNextMoveResponse(
                        new Position(4, 3),
                        0.99,
                        13.05
                );

        when(aiClient.gameNextMove(request))
                .thenReturn(expectedResponse);

        // when
        AiGameNextMoveResponse response =
                aiService.gameNextMove(request);

        // then
        assertThat(response)
                .isEqualTo(expectedResponse);

        verify(aiClient).gameNextMove(request);
    }
}