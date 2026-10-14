package com.electricwealth.api.web;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.electricwealth.api.config.AppProperties;
import com.electricwealth.api.error.ErrorResponses;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * At most {@code ew.rate-limit-per-minute} (default 300) requests per minute per client IP, then 429 RATE_LIMITED.
 * Runs before authentication.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private record Bucket(int count, long resetAt) {
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ErrorResponses errorResponses;
    private final int maxRequests;

    public RateLimitFilter(ErrorResponses errorResponses, AppProperties props) {
        this.errorResponses = errorResponses;
        this.maxRequests = props.rateLimitPerMinute();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.compute(request.getRemoteAddr(), (ip, b) -> b == null || now > b.resetAt()
                ? new Bucket(1, now + WINDOW_MS)
                : new Bucket(b.count() + 1, b.resetAt()));
        if (bucket.count() > maxRequests) {
            errorResponses.write(request, response, 429, "RATE_LIMITED", "Too many requests. Please try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
