package com.kdh.solvego.domain.problem.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProblemControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("JWT로 인증된 사용자는 문제를 등록할 수 있다")
    void create_problem_success_with_jwt() throws Exception {
        // given
        String accessToken = signupAndLogin("username1", "1234");

        // when & then
        mockMvc.perform(post("/api/problems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemCreateRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.problemId").exists());
    }

    @Test
    @DisplayName("문제 등록 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void create_problem_fails_when_request_is_invalid() throws Exception {
        // given
        String accessToken = signupAndLogin("username4", "1234");

        String invalidRequestBody = """
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JWT 없이 문제를 등록하면 401 Unauthorized를 반환한다")
    void create_problem_fails_without_jwt() throws Exception {
        mockMvc.perform(post("/api/problems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemCreateRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("기본 페이지 설정으로 문제 목록을 조회할 수 있다")
    void get_problems_success() throws Exception {
        // given
        String accessToken = signupAndLogin("username2", "1234");
        Long problemId = createProblem(accessToken);

        // when & then
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems[0].problemId").value(problemId))
                .andExpect(jsonPath("$.problems[0].title").value("problem title"))
                .andExpect(jsonPath("$.problems[0].creatorName").value("username2"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("요청한 페이지 번호와 크기로 문제 목록을 조회할 수 있다")
    void get_problems_with_pagination_success() throws Exception {
        // given
        String accessToken = signupAndLogin("username5", "1234");

        Long firstProblemId = createProblem(accessToken);
        Long secondProblemId = createProblem(accessToken);
        Long thirdProblemId = createProblem(accessToken);

        // when & then
        mockMvc.perform(get("/api/problems")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems.length()").value(2))
                .andExpect(jsonPath("$.problems[0].problemId").value(thirdProblemId))
                .andExpect(jsonPath("$.problems[1].problemId").value(secondProblemId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/problems")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems.length()").value(1))
                .andExpect(jsonPath("$.problems[0].problemId").value(firstProblemId))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("레거시 API로 전체 문제 목록을 조회할 수 있다")
    void get_problems_legacy_success() throws Exception {
        // given
        String accessToken = signupAndLogin("username6", "1234");
        Long problemId = createProblem(accessToken);

        // when & then
        mockMvc.perform(get("/api/problems/legacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems[0].problemId").value(problemId))
                .andExpect(jsonPath("$.problems[0].title").value("problem title"));
    }

    @Test
    @DisplayName("문제 상세를 조회할 수 있다")
    void get_problem_success() throws Exception {
        // given
        String accessToken = signupAndLogin("username3", "1234");
        Long problemId = createProblem(accessToken);

        // when & then
        mockMvc.perform(get("/api/problems/{problemId}", problemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("problem title"))
                .andExpect(jsonPath("$.description").value("problem description"));
    }

    @Test
    @DisplayName("존재하지 않는 문제를 조회하면 404 Not Found를 반환한다")
    void get_problem_fails_when_problem_not_found() throws Exception {
        mockMvc.perform(get("/api/problems/{problemId}", 999999L))
                .andExpect(status().isNotFound());
    }


    @Test
    @DisplayName("문제 작성자는 문제를 수정할 수 있다")
    void update_problem_success() throws Exception {
        // given
        String accessToken = signupAndLogin("updateOwner", "1234");
        Long problemId = createProblem(accessToken);

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", problemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemUpdateRequestJson()))
                .andExpect(status().isNoContent());

        // 수정 내용이 실제 DB에 반영됐는지 조회 API로 확인
        mockMvc.perform(get("/api/problems/{problemId}", problemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("updated problem title"))
                .andExpect(jsonPath("$.description").value("updated problem description"))
                .andExpect(jsonPath("$.nextPlayer").value("WHITE"));
    }

    @Test
    @DisplayName("문제 수정 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void update_problem_fails_when_request_is_invalid() throws Exception {
        // given
        String accessToken = signupAndLogin("invalidUpdateOwner", "1234");
        Long problemId = createProblem(accessToken);

        String invalidRequestBody = """
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
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JWT 없이 문제를 수정하면 401 Unauthorized를 반환한다")
    void update_problem_fails_without_jwt() throws Exception {
        // given
        String ownerAccessToken = signupAndLogin("unauthorizedOwner", "1234");
        Long problemId = createProblem(ownerAccessToken);

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", problemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemUpdateRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 문제를 수정하면 404 Not Found를 반환한다")
    void update_problem_fails_when_problem_not_found() throws Exception {
        // given
        String accessToken = signupAndLogin("notFoundUpdateUser", "1234");

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemUpdateRequestJson()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("문제 작성자가 아니면 문제를 수정할 수 없다")
    void update_problem_fails_when_user_is_not_creator() throws Exception {
        // given
        String ownerAccessToken = signupAndLogin("problemOwner", "1234");
        Long problemId = createProblem(ownerAccessToken);

        String otherUserAccessToken = signupAndLogin("otherUser", "1234");

        // when & then
        mockMvc.perform(put("/api/problems/{problemId}", problemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherUserAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemUpdateRequestJson()))
                .andExpect(status().isForbidden());

        // 수정되지 않았는지 확인
        mockMvc.perform(get("/api/problems/{problemId}", problemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("problem title"))
                .andExpect(jsonPath("$.description").value("problem description"))
                .andExpect(jsonPath("$.nextPlayer").value("BLACK"));
    }


    private String signupAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequestJson(username, password)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String responseBody = result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String accessToken = jsonNode.get("accessToken").asText();

        assertThat(accessToken).isNotBlank();

        return accessToken;
    }

    private Long createProblem(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/problems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemCreateRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.problemId").exists())
                .andReturn();

        String responseBody = result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode jsonNode = objectMapper.readTree(responseBody);

        return jsonNode.get("problemId").asLong();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String signupRequestJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    private String loginRequestJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    private String problemCreateRequestJson() {
        return """
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
    }

    private String problemUpdateRequestJson() {
        return """
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
    }

}