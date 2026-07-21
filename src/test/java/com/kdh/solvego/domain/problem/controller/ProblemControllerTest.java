package com.kdh.solvego.domain.problem.controller;

import com.kdh.solvego.domain.problem.dto.ProblemCreateRequest;
import com.kdh.solvego.domain.problem.dto.ProblemCreateResponse;
import com.kdh.solvego.domain.problem.exception.ProblemNotFoundException;
import com.kdh.solvego.domain.problem.service.ProblemService;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import com.kdh.solvego.domain.problem.dto.ProblemUpdateRequest;
import com.kdh.solvego.domain.problem.exception.ProblemAccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@WebMvcTest(ProblemController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProblemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemService problemService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("문제 등록에 성공하면 201 Created를 반환한다")
    void create_problem_success() throws Exception {
        // given
        Long userId = 1L;

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        String requestBody = """
                {
                  "title": "problem title",
                  "description": "problem description",
                  "blackStones": [
                    { "x": 3, "y": 3 }
                  ],
                  "whiteStones": [
                    { "x": 4, "y": 4 }
                  ],
                  "nextPlayer": "BLACK",
                  "answerPosition": {
                    "x": 10,
                    "y": 10
                  }
                }
                """;

        when(problemService.createProblem(eq(userId), any(ProblemCreateRequest.class)))
                .thenReturn(new ProblemCreateResponse(1L));

        // when & then
        mockMvc.perform(post("/api/problems")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        verify(problemService).createProblem(eq(userId), any(ProblemCreateRequest.class));
    }

    @Test
    @DisplayName("문제 등록 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void create_problem_fails_when_request_is_invalid() throws Exception {
        // given
        Long userId = 1L;

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        String requestBody = """
                {
                  "title": "",
                  "description": "problem description",
                  "blackStones": [
                    { "x": 3, "y": 3 }
                  ],
                  "whiteStones": [
                    { "x": 4, "y": 4 }
                  ],
                  "nextPlayer": "BLACK",
                  "answerPosition": {
                    "x": 10,
                    "y": 10
                  }
                }
                """;

        // when & then
        mockMvc.perform(post("/api/problems")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(problemService, never())
                .createProblem(any(Long.class), any(ProblemCreateRequest.class));
    }

    @Test
    @DisplayName("기본 페이지 설정으로 문제 목록 조회에 성공한다")
    void get_problems_success() throws Exception {
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk());

        verify(problemService).getProblems(0, 20);
    }

    @Test
    @DisplayName("요청한 페이지 번호와 크기로 문제 목록을 조회한다")
    void get_problems_with_page_and_size_success() throws Exception {
        mockMvc.perform(get("/api/problems")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(problemService).getProblems(2, 10);
    }


    @Test
    @DisplayName("레거시 전체 문제 목록 조회에 성공한다")
    void get_problems_legacy_success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/problems/legacy"))
                .andExpect(status().isOk());

        verify(problemService).getProblems();
    }

    @Test
    @DisplayName("문제 상세 조회에 성공한다")
    void get_problem_success() throws Exception {
        // given
        Long problemId = 1L;

        // when & then
        mockMvc.perform(get("/api/problems/{problemId}", problemId))
                .andExpect(status().isOk());

        verify(problemService).getProblem(problemId);
    }

    @Test
    @DisplayName("존재하지 않는 문제를 조회하면 404 Not Found를 반환한다")
    void get_problem_fails_when_problem_not_found() throws Exception {
        // given
        Long problemId = 999L;

        when(problemService.getProblem(problemId))
                .thenThrow(new ProblemNotFoundException());

        // when & then
        mockMvc.perform(get("/api/problems/{problemId}", problemId))
                .andExpect(status().isNotFound());

        verify(problemService).getProblem(problemId);
    }

    @Test
    @DisplayName("문제 수정에 성공하면 204 No Content를 반환한다")
    void update_problem_success() throws Exception {
        // given
        Long userId = 1L;
        Long problemId = 10L;

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        String requestBody = """
            {
              "title": "updated problem title",
              "description": "updated problem description",
              "blackStones": [
                { "x": 5, "y": 5 }
              ],
              "whiteStones": [
                { "x": 6, "y": 6 }
              ],
              "nextPlayer": "WHITE",
              "answerPosition": {
                "x": 11,
                "y": 11
              }
            }
            """;

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", problemId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());

        verify(problemService)
                .updateProblem(
                        eq(userId),
                        eq(problemId),
                        any(ProblemUpdateRequest.class)
                );
    }

    @Test
    @DisplayName("문제 수정 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void update_problem_fails_when_request_is_invalid() throws Exception {
        // given
        Long userId = 1L;
        Long problemId = 10L;

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        String requestBody = """
            {
              "title": "",
              "description": "updated problem description",
              "blackStones": [
                { "x": 5, "y": 5 }
              ],
              "whiteStones": [
                { "x": 6, "y": 6 }
              ],
              "nextPlayer": "WHITE",
              "answerPosition": {
                "x": 11,
                "y": 11
              }
            }
            """;

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", problemId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(problemService, never())
                .updateProblem(
                        any(Long.class),
                        any(Long.class),
                        any(ProblemUpdateRequest.class)
                );
    }

    @Test
    @DisplayName("존재하지 않는 문제를 수정하면 404 Not Found를 반환한다")
    void update_problem_fails_when_problem_not_found() throws Exception {
        // given
        Long userId = 1L;
        Long problemId = 999L;

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        String requestBody = """
            {
              "title": "updated problem title",
              "description": "updated problem description",
              "blackStones": [
                { "x": 5, "y": 5 }
              ],
              "whiteStones": [
                { "x": 6, "y": 6 }
              ],
              "nextPlayer": "WHITE",
              "answerPosition": {
                "x": 11,
                "y": 11
              }
            }
            """;

        doThrow(new ProblemNotFoundException())
                .when(problemService)
                .updateProblem(
                        eq(userId),
                        eq(problemId),
                        any(ProblemUpdateRequest.class)
                );

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", problemId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());

        verify(problemService)
                .updateProblem(
                        eq(userId),
                        eq(problemId),
                        any(ProblemUpdateRequest.class)
                );
    }

    @Test
    @DisplayName("문제 작성자가 아니면 403 Forbidden을 반환한다")
    void update_problem_fails_when_user_is_not_creator() throws Exception {
        // given
        Long userId = 2L;
        Long problemId = 10L;

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        String requestBody = """
            {
              "title": "updated problem title",
              "description": "updated problem description",
              "blackStones": [
                { "x": 5, "y": 5 }
              ],
              "whiteStones": [
                { "x": 6, "y": 6 }
              ],
              "nextPlayer": "WHITE",
              "answerPosition": {
                "x": 11,
                "y": 11
              }
            }
            """;

        doThrow(new ProblemAccessDeniedException())
                .when(problemService)
                .updateProblem(
                        eq(userId),
                        eq(problemId),
                        any(ProblemUpdateRequest.class)
                );

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", problemId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        verify(problemService)
                .updateProblem(
                        eq(userId),
                        eq(problemId),
                        any(ProblemUpdateRequest.class)
                );
    }

}