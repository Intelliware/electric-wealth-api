package com.electricwealth.api.security;

/**
 * The signed-in user, available in controllers as {@code @AuthenticationPrincipal AuthenticatedUser user}
 * (the equivalent of {@code req.userId} and {@code req.accessToken} in the Node API).
 */
public record AuthenticatedUser(String userId, String accessToken) {
}
