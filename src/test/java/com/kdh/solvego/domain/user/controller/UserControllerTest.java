package com.kdh.solvego.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.attempt.service.AttemptService;
import com.kdh.solvego.domain.problem.dto.WrongProblemResponse;
import com.kdh.solvego.domain.user.dto.MyPageProblemResponse;
import com.kdh.solvego.domain.user.dto.MyPageResponse;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.dto.PasswordChangeRequest;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.dto.SignupResponse;
import com.kdh.solvego.domain.user.exception.DuplicateUsernameException;
import com.kdh.solvego.domain.user.service.UserService;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AttemptService attemptService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("회원가입에 성공하면 201 Created와 userId를 반환한다")
    void signup_success() throws Exception {
        // given
        SignupRequest request = new SignupRequest("username", "1234");

        when(userService.signup(any(SignupRequest.class)))
                .thenReturn(new SignupResponse(1L));

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(1L));

        verify(userService).signup(request);
    }

    @Test
    @DisplayName("회원가입 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void signup_fails_when_request_is_invalid() throws Exception {
        // given
        SignupRequest request = new SignupRequest("", "1234");

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).signup(any(SignupRequest.class));
    }

    @Test
    @DisplayName("중복된 username이면 409 Conflict를 반환한다")
    void signup_fails_when_username_is_duplicated() throws Exception {
        // given
        SignupRequest request = new SignupRequest("username", "1234");

        when(userService.signup(any(SignupRequest.class)))
                .thenThrow(new DuplicateUsernameException());

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(userService).signup(request);
    }

    @Test
    @DisplayName("틀린 문제 목록 조회에 성공한다")
    void get_wrong_problems_success() throws Exception {
        // given
        Long userId = 1L;

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        List<WrongProblemResponse> response = List.of(
                new WrongProblemResponse(1L, "problem title")
        );

        when(attemptService.getWrongProblems(userId))
                .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/users/me/wrong-problems")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].problemId").value(1L))
                .andExpect(jsonPath("$[0].title").value("problem title"));

        verify(attemptService).getWrongProblems(userId);
    }

    @Test
    @DisplayName("마이페이지 정보를 조회한다")
    void get_my_page_success() throws Exception {
        Long userId = 1L;
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        when(userService.getMyPage(userId)).thenReturn(new MyPageResponse(
                "username",
                LocalDateTime.of(2026, 1, 2, 3, 4),
                2,
                3,
                1,
                SubscriptionPlan.FREE,
                List.of(new MyPageProblemResponse(5L, "내 문제"))
        ));

        mockMvc.perform(get("/api/users/me").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("username"))
                .andExpect(jsonPath("$.registeredProblemCount").value(2))
                .andExpect(jsonPath("$.solvedProblemCount").value(3))
                .andExpect(jsonPath("$.wrongProblemCount").value(1))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.problems[0].problemId").value(5L));

        verify(userService).getMyPage(userId);
    }

    @Test
    @DisplayName("현재 비밀번호와 새 비밀번호로 비밀번호를 변경한다")
    void change_password_success() throws Exception {
        Long userId = 1L;
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        PasswordChangeRequest request = new PasswordChangeRequest("old", "new");

        mockMvc.perform(put("/api/users/me/password")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(userId, request);
    }

    @Test
    @DisplayName("회원 탈퇴를 요청한다")
    void delete_account_success() throws Exception {
        Long userId = 1L;
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());

        mockMvc.perform(delete("/api/users/me").principal(authentication))
                .andExpect(status().isNoContent());

        verify(userService).deleteAccount(userId);
    }
}
