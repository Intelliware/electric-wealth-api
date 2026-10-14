package com.electricwealth.api.security;

import java.util.List;

import com.electricwealth.api.config.AppProperties;
import com.electricwealth.api.error.ErrorResponses;
import com.electricwealth.api.repository.SessionRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    /**
     * Everything under these paths needs a signed-in user, even paths that don't exist (the Node API applies
     * its auth middleware to whole routers, so an unknown {@code /api/wealth/...} path is a 401, not a 404).
     * Auth, registration, health and the API docs are public.
     */
    private static final String[] PROTECTED = {
        "/api/wealth/**",
        "/api/profile/**",
        "/api/preferences/**",
        "/api/notifications/**",
        "/api/education/**",
        "/api/advisor/**",
        "/api/market/**",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRepository sessions,
            ErrorResponses errorResponses) throws Exception {
        return http
                // A stateless JSON API authenticated by bearer tokens: no cookies, so no CSRF, sessions or login pages.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PROTECTED).authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new BearerTokenFilter(sessions), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> errorResponses
                        .write(request, response, 401, "UNAUTHORIZED", "Access token is missing or expired.")))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(props.corsOrigins());
        config.setAllowedMethods(List.of("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
