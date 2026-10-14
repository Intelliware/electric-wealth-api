package com.electricwealth.api.security;

import java.io.IOException;
import java.util.List;

import com.electricwealth.api.repository.SessionRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs the request in when it carries an active, MFA-verified access token from the {@code sessions} table.
 * It never rejects a request itself: {@link SecurityConfig} decides which paths need a signed-in user.
 * <p>
 * Deliberately not a {@code @Component}, so Spring Boot doesn't also register it as a plain servlet filter;
 * it runs only inside the Spring Security filter chain.
 */
class BearerTokenFilter extends OncePerRequestFilter {

    private final SessionRepository sessions;

    BearerTokenFilter(SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = BearerTokens.from(request);
        if (token != null) {
            sessions.findActive(token)
                    .filter(SessionRepository.SessionRecord::mfaVerified)
                    .ifPresent(session -> {
                        var user = new AuthenticatedUser(session.userId(), token);
                        var auth = UsernamePasswordAuthenticationToken.authenticated(user, token, List.of());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
