package com.kdh.solvego.domain.auth.service;

import com.kdh.solvego.domain.auth.dto.LoginRequest;
import com.kdh.solvego.domain.auth.dto.AuthTokens;
import com.kdh.solvego.domain.auth.exception.InvalidLoginException;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshSessionService refreshSessions;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("로그인에 성공한다")
    void login_success() {
        // given
        LoginRequest request = new LoginRequest("username", "1234");

        User user = new User("username", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(userRepository.findByUsername("username"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("1234", "encoded-password"))
                .thenReturn(true);

        when(jwtTokenProvider.createToken(1L))
                .thenReturn("access-token");

        // when
        AuthTokens response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");

        verify(userRepository).findByUsername("username");
        verify(passwordEncoder).matches("1234", "encoded-password");
        verify(jwtTokenProvider).createToken(1L);
    }

    @Test
    @DisplayName("존재하지 않는 username이면 예외가 발생한다")
    void login_fails_when_username_not_found() {
        // given
        LoginRequest request = new LoginRequest("unknown", "1234");

        when(userRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidLoginException.class);

        verify(userRepository).findByUsername("unknown");
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 예외가 발생한다")
    void login_fails_when_password_mismatch() {
        // given
        LoginRequest request = new LoginRequest("username", "wrong");

        User user = new User("username", "encoded-password");
        ReflectionTestUtils.setField(user, "id", 1L);

        when(userRepository.findByUsername("username"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("wrong", "encoded-password"))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidLoginException.class);

        verify(userRepository).findByUsername("username");
        verify(passwordEncoder).matches("wrong", "encoded-password");
        verifyNoInteractions(jwtTokenProvider);
    }
}