package com.kdh.solvego.domain.auth.controller;

import com.kdh.solvego.domain.auth.service.RefreshSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookie {
    public static final String NAME = "solvego_refresh";
    private final boolean secure;
    private final String sameSite;

    public RefreshCookie(@Value("${auth.cookie.secure:false}") boolean secure,
                         @Value("${auth.cookie.same-site:Lax}") String sameSite) {
        if (!sameSite.equals("Lax") && !sameSite.equals("None")) {
            throw new IllegalArgumentException("Unsupported refresh cookie SameSite");
        }
        if (sameSite.equals("None") && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public String issue(String token) { return build(token, RefreshSessionService.LIFETIME); }
    public String clear() { return build("", Duration.ZERO); }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure)
                .sameSite(sameSite).path("/api/auth").maxAge(maxAge).build().toString();
    }
}
