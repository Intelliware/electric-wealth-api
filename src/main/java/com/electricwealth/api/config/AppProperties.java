package com.electricwealth.api.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code ew.*} in application.yml (the equivalent of the Node API's config.ts). */
@ConfigurationProperties("ew")
public record AppProperties(String dbPath, List<String> corsOrigins, boolean logRequests, int rateLimitPerMinute) {
}
