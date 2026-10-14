package com.electricwealth.api.security;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

public final class BearerTokens {

    private BearerTokens() {
    }

    /** The token from an {@code Authorization: Bearer <token>} header, or null. */
    public static @Nullable String from(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
