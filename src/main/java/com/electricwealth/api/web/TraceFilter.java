package com.electricwealth.api.web;

import java.io.IOException;

import com.electricwealth.api.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs first: assigns the trace id and logs one line per request once the response is done. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("requests");

    private final boolean logRequests;

    public TraceFilter(AppProperties props) {
        this.logRequests = props.logRequests();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceIds.newId();
        request.setAttribute(TraceIds.ATTRIBUTE, traceId);
        response.setHeader("X-Trace-Id", traceId);
        MDC.put("traceId", traceId);
        long started = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            if (logRequests) {
                log.info("{} {} {} {}ms trace={}", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), System.currentTimeMillis() - started, traceId);
            }
            MDC.remove("traceId");
        }
    }
}
