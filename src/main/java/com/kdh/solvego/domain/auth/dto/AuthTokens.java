package com.kdh.solvego.domain.auth.dto;

// Internal result only: the refresh token must never be serialized into a response body.
public record AuthTokens(String accessToken, String refreshToken) {}
