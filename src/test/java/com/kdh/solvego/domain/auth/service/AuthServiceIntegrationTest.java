package com.kdh.solvego.domain.auth.service;

import com.kdh.solvego.domain.auth.dto.LoginRequest;
import com.kdh.solvego.domain.auth.dto.AuthTokens;
import com.kdh.solvego.domain.auth.exception.InvalidLoginException;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.dto.SignupResponse;
import com.kdh.solvego.domain.user.service.UserService;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("로그인에 성공하면 accessToken을 반환한다")
    void login_success() {
        // given
        SignupResponse signupResponse = userService.signup(
                new SignupRequest("username", "1234")
        );

        entityManager.flush();
        entityManager.clear();

        LoginRequest request = new LoginRequest("username", "1234");

        // when
        AuthTokens response = authService.login(request);

        // then
        assertThat(response.accessToken()).isNotBlank();

        Long userId = jwtTokenProvider.getUserId(response.accessToken());

        assertThat(userId).isEqualTo(signupResponse.userId());
    }

    @Test
    @DisplayName("존재하지 않는 username이면 로그인에 실패한다")
    void login_fails_when_username_not_found() {
        // given
        LoginRequest request = new LoginRequest("unknown", "1234");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidLoginException.class);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다")
    void login_fails_when_password_mismatch() {
        // given
        userService.signup(new SignupRequest("username", "1234"));

        entityManager.flush();
        entityManager.clear();

        LoginRequest request = new LoginRequest("username", "wrong-password");

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidLoginException.class);
    }
}