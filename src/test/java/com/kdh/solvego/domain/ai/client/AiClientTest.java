package com.kdh.solvego.domain.ai.client;

import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveAiResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveRequest;
import com.kdh.solvego.domain.ai.dto.AiExplanationRequest;
import com.kdh.solvego.domain.ai.dto.AiExplanationResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;
import com.kdh.solvego.domain.ai.exception.AiServerException;
import com.kdh.solvego.domain.ai.exception.AiTimeoutException;
import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.ai.type.Player;
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
    @DisplayName("AI 추천 요청에 성공하면 추천 수와 분석 정보를 반환한다")
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
                          "bestWinRate": 0.48,
                          "scoreLead": 2.5,
                          "candidates": [
                            {
                              "move": {
                                "x": 15,
                                "y": 3
                              },
                              "winRate": 0.48,
                              "scoreLead": 2.5,
                              "visits": 10,
                              "pv": [
                                {
                                  "x": 15,
                                  "y": 3
                                },
                                {
                                  "x": 3,
                                  "y": 15
                                }
                              ]
                            }
                          ]
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

        assertThat(response.scoreLead())
                .isEqualTo(2.5);

        assertThat(response.candidates())
                .hasSize(1);

        AiRecommendResponse.Candidate candidate =
                response.candidates().get(0);

        assertThat(candidate.move())
                .isEqualTo(new Position(15, 3));

        assertThat(candidate.winRate())
                .isEqualTo(0.48);

        assertThat(candidate.scoreLead())
                .isEqualTo(2.5);

        assertThat(candidate.visits())
                .isEqualTo(10);

        assertThat(candidate.pv())
                .containsExactly(
                        new Position(15, 3),
                        new Position(3, 15)
                );

        mockServer.verify();
    }

    @Test
    @DisplayName("AI가 PASS를 반환하면 null로 역직렬화한다")
    void recommend_success_when_ai_passes() {
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
                          "bestMove": null,
                          "bestWinRate": 0.72,
                          "scoreLead": 5.5,
                          "candidates": [
                            {
                              "move": null,
                              "winRate": 0.72,
                              "scoreLead": 5.5,
                              "visits": 10,
                              "pv": [
                                null,
                                {
                                  "x": 3,
                                  "y": 3
                                }
                              ]
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // when
        AiRecommendResponse response = aiClient.recommend(request);

        // then
        assertThat(response.bestMove())
                .isNull();

        assertThat(response.bestWinRate())
                .isEqualTo(0.72);

        assertThat(response.scoreLead())
                .isEqualTo(5.5);

        assertThat(response.candidates())
                .hasSize(1);

        AiRecommendResponse.Candidate candidate =
                response.candidates().get(0);

        assertThat(candidate.move())
                .isNull();

        assertThat(candidate.winRate())
                .isEqualTo(0.72);

        assertThat(candidate.scoreLead())
                .isEqualTo(5.5);

        assertThat(candidate.visits())
                .isEqualTo(10);

        assertThat(candidate.pv())
                .containsExactly(
                        null,
                        new Position(3, 3)
                );

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
                          "winRateLoss": 0.07,
                          "scoreLead": 2.5,
                          "candidates": [
                            {
                              "move": {
                                "x": 15,
                                "y": 3
                              },
                              "winRate": 0.48,
                              "scoreLead": 2.5,
                              "visits": 10,
                              "pv": [
                                {
                                  "x": 15,
                                  "y": 3
                                },
                                {
                                  "x": 3,
                                  "y": 15
                                }
                              ]
                            }
                          ]
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

        assertThat(response.scoreLead())
                .isEqualTo(2.5);

        assertThat(response.candidates())
                .hasSize(1);

        AiAnalyzeResponse.Candidate candidate =
                response.candidates().get(0);

        assertThat(candidate.move())
                .isEqualTo(new Position(15, 3));

        assertThat(candidate.winRate())
                .isEqualTo(0.48);

        assertThat(candidate.scoreLead())
                .isEqualTo(2.5);

        assertThat(candidate.visits())
                .isEqualTo(10);

        assertThat(candidate.pv())
                .containsExactly(
                        new Position(15, 3),
                        new Position(3, 15)
                );

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

    @Test
    @DisplayName("AI 대국 다음 수 요청에 성공하면 AI의 착수와 분석 정보를 반환한다")
    void gameNextMove_success() {
        // given
        AiGameNextMoveRequest request = new AiGameNextMoveRequest(
                List.of(
                        new AiGameNextMoveRequest.Move(
                                Player.BLACK,
                                MoveType.PLAY,
                                new Position(3, 15)
                        ),
                        new AiGameNextMoveRequest.Move(
                                Player.WHITE,
                                MoveType.PLAY,
                                new Position(15, 3)
                        ),
                        new AiGameNextMoveRequest.Move(
                                Player.BLACK,
                                MoveType.PASS,
                                null
                        )
                )
        );

        mockServer.expect(requestTo("http://localhost:8000/game/next-move"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "moveType": "PLAY",
                          "move": {
                            "x": 4,
                            "y": 3
                          },
                          "winRate": 0.99,
                          "scoreLead": 13.05,
                          "evidenceToken": "signed-evidence",
                          "candidates": [{
                            "id": "c1",
                            "rank": 1,
                            "moveType": "PLAY",
                            "move": {"x": 4, "y": 3},
                            "winRate": 0.99,
                            "scoreLead": 13.05,
                            "visits": 5,
                            "pv": [{"x": 4, "y": 3}]
                          }]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // when
        AiGameNextMoveAiResponse response =
                aiClient.gameNextMove(request);

        // then
        assertThat(response.moveType())
                .isEqualTo(MoveType.PLAY);

        assertThat(response.move())
                .isEqualTo(new Position(4, 3));

        assertThat(response.winRate())
                .isEqualTo(0.99);

        assertThat(response.scoreLead())
                .isEqualTo(13.05);

        assertThat(response.evidenceToken()).isEqualTo("signed-evidence");
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).id()).isEqualTo("c1");

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 대국에서 AI가 PASS하면 moveType은 PASS이고 move는 null이다")
    void gameNextMove_success_when_ai_passes() {
        // given
        AiGameNextMoveRequest request = new AiGameNextMoveRequest(
                List.of(
                        new AiGameNextMoveRequest.Move(
                                Player.BLACK,
                                MoveType.PLAY,
                                new Position(3, 15)
                        )
                )
        );

        mockServer.expect(requestTo("http://localhost:8000/game/next-move"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "moveType": "PASS",
                          "move": null,
                          "winRate": 0.82,
                          "scoreLead": 10.5
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        // when
        AiGameNextMoveAiResponse response =
                aiClient.gameNextMove(request);

        // then
        assertThat(response.moveType())
                .isEqualTo(MoveType.PASS);

        assertThat(response.move())
                .isNull();

        assertThat(response.winRate())
                .isEqualTo(0.82);

        assertThat(response.scoreLead())
                .isEqualTo(10.5);

        mockServer.verify();
    }

    @Test
    @DisplayName("AI 대국 서버가 504를 반환하면 AiTimeoutException이 발생한다")
    void gameNextMove_fails_when_ai_server_times_out() {
        // given
        AiGameNextMoveRequest request = new AiGameNextMoveRequest(
                List.of()
        );

        mockServer.expect(requestTo("http://localhost:8000/game/next-move"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        // when & then
        assertThatThrownBy(() -> aiClient.gameNextMove(request))
                .isInstanceOf(AiTimeoutException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("서명된 근거 토큰으로 AI 착수 해설을 요청한다")
    void explain_success() {
        AiExplanationRequest request = new AiExplanationRequest("signed-evidence");
        mockServer.expect(requestTo("http://localhost:8000/game/explanation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "source": "TEMPLATE",
                          "perspective": "WHITE",
                          "candidates": [],
                          "explanation": {
                            "summary": "요약",
                            "comparison": "비교",
                            "pvExplanation": "예상 진행",
                            "limitation": "낮은 탐색량",
                            "evidenceRefs": ["c1"]
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AiExplanationResponse response = aiClient.explain(request);

        assertThat(response.source()).isEqualTo("TEMPLATE");
        assertThat(response.perspective()).isEqualTo(Player.WHITE);
        assertThat(response.explanation().evidenceRefs()).containsExactly("c1");
        mockServer.verify();
    }
}
