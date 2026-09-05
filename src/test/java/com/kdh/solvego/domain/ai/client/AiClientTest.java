package com.kdh.solvego.domain.ai.client;

import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.exception.AiServerException;
import com.kdh.solvego.domain.ai.exception.AiTimeoutException;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiClientTest {

    private AiClient aiClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        aiClient = new AiClient(
                builder,
                "http://localhost:8000"
        );
    }

    @Test
    @DisplayName("AI 추천 요청에 성공하면 추천 좌표와 승률을 반환한다")
    void recommend_success() {
        // given
        AiRecommendRequest request = new AiRecommendRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK
        );

        mockServer.expect(requestTo("http://localhost:8000/recommend"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "bestMove": {
                            "x": 15,
                            "y": 3
                          },
                          "bestWinRate": 0.48
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // when
        AiRecommendResponse response = aiClient.recommend(request);

        // then
        assertThat(response.bestMove())
                .isEqualTo(new Position(15, 3));

        assertThat(response.bestWinRate())
                .isEqualTo(0.48);

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 수 분석 요청에 성공하면 최선의 수와 선택한 수의 분석 결과를 반환한다")
    void analyze_success() {
        // given
        AiAnalyzeRequest request = new AiAnalyzeRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        mockServer.expect(requestTo("http://localhost:8000/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "bestMove": {
                            "x": 15,
                            "y": 3
                          },
                          "selectedMove": {
                            "x": 10,
                            "y": 10
                          },
                          "bestWinRate": 0.48,
                          "selectedWinRate": 0.41,
                          "winRateLoss": 0.07
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // when
        AiAnalyzeResponse response = aiClient.analyze(request);

        // then
        assertThat(response.bestMove())
                .isEqualTo(new Position(15, 3));

        assertThat(response.selectedMove())
                .isEqualTo(new Position(10, 10));

        assertThat(response.bestWinRate())
                .isEqualTo(0.48);

        assertThat(response.selectedWinRate())
                .isEqualTo(0.41);

        assertThat(response.winRateLoss())
                .isEqualTo(0.07);

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 추천 서버가 504를 반환하면 AiTimeoutException이 발생한다")
    void recommend_fails_when_ai_server_times_out() {
        // given
        AiRecommendRequest request = new AiRecommendRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK
        );

        mockServer.expect(requestTo("http://localhost:8000/recommend"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        // when & then
        assertThatThrownBy(() -> aiClient.recommend(request))
                .isInstanceOf(AiTimeoutException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 분석 서버가 504를 반환하면 AiTimeoutException이 발생한다")
    void analyze_fails_when_ai_server_times_out() {
        // given
        AiAnalyzeRequest request = new AiAnalyzeRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        mockServer.expect(requestTo("http://localhost:8000/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        // when & then
        assertThatThrownBy(() -> aiClient.analyze(request))
                .isInstanceOf(AiTimeoutException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 추천 서버가 오류를 반환하면 AiServerException이 발생한다")
    void recommend_fails_when_ai_server_returns_error() {
        // given
        AiRecommendRequest request = new AiRecommendRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK
        );

        mockServer.expect(requestTo("http://localhost:8000/recommend"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertThatThrownBy(() -> aiClient.recommend(request))
                .isInstanceOf(AiServerException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 분석 서버가 오류를 반환하면 AiServerException이 발생한다")
    void analyze_fails_when_ai_server_returns_error() {
        // given
        AiAnalyzeRequest request = new AiAnalyzeRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        mockServer.expect(requestTo("http://localhost:8000/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertThatThrownBy(() -> aiClient.analyze(request))
                .isInstanceOf(AiServerException.class);

        mockServer.verify();
    }
    @Test
    @DisplayName("AI 서버 상태 조회에 성공하면 ONLINE을 반환한다")
    void status_success() {
        // given
        mockServer.expect(requestTo("http://localhost:8000/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "status": "ok"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // when
        AiStatusResponse response = aiClient.status();

        // then
        assertThat(response.status())
                .isEqualTo("ONLINE");

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 서버 상태 조회 중 오류가 발생하면 OFFLINE을 반환한다")
    void status_returns_offline_when_ai_server_returns_error() {
        // given
        mockServer.expect(requestTo("http://localhost:8000/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when
        AiStatusResponse response = aiClient.status();

        // then
        assertThat(response.status())
                .isEqualTo("OFFLINE");

        mockServer.verify();
    }
}