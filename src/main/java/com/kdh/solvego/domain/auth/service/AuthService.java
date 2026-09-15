package com.kdh.solvego.domain.auth.service;

import com.kdh.solvego.domain.auth.dto.LoginRequest;
import com.kdh.solvego.domain.auth.dto.LoginResponse;
import com.kdh.solvego.domain.auth.dto.AuthTokens;
import com.kdh.solvego.domain.auth.exception.InvalidLoginException;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    private final RefreshSessionService refreshSessions;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, RefreshSessionService refreshSessions) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshSessions = refreshSessions;
    }

    public AuthTokens login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidLoginException::new);

        if (!user.matchesPassword(
                request.password(),
                passwordEncoder
        )) {
            throw new InvalidLoginException();
        }

        String accessToken= jwtTokenProvider.createToken(user.getId());
        return new AuthTokens(accessToken, refreshSessions.create(user.getId()));
    }

    public LoginResponse refresh(String token) {
        long userId = refreshSessions.validate(token);
        return new LoginResponse(jwtTokenProvider.createToken(userId));
    }

    public void logout(String token) { refreshSessions.revoke(token); }
}
