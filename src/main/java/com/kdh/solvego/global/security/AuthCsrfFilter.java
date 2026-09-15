package com.kdh.solvego.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Set;

// Browser-only cookie endpoints: strict Origin + non-simple header, before any mutation.
public class AuthCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");
    private final Set<String> allowedOrigins;

    public AuthCsrfFilter(Set<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && PATHS.contains(UrlPathHelper.defaultInstance.getPathWithinApplication(request))) {
            String origin = request.getHeader("Origin");
            boolean json;
            try {
                json = request.getContentType() != null && MediaType.APPLICATION_JSON
                        .isCompatibleWith(MediaType.parseMediaType(request.getContentType()));
            } catch (IllegalArgumentException e) { json = false; }
            if (origin == null || "null".equals(origin) || !allowedOrigins.contains(origin)
                    || !"1".equals(request.getHeader("X-SolveGO-CSRF")) || !json) {
                response.setStatus(403);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
