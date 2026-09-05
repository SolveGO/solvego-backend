package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.ai.client.AiClient;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
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
                        0.48
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
                        0.07
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
}