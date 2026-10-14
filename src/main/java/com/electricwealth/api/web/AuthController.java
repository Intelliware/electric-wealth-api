package com.electricwealth.api.web;

import static com.electricwealth.api.repository.SessionRepository.TOKEN_TTL_SECONDS;

import java.util.Optional;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.dto.AuthDtos.LogoutRequest;
import com.electricwealth.api.dto.AuthDtos.MfaVerifyRequest;
import com.electricwealth.api.dto.AuthDtos.MfaVerifyResponse;
import com.electricwealth.api.dto.AuthDtos.RefreshRequest;
import com.electricwealth.api.dto.AuthDtos.RefreshResponse;
import com.electricwealth.api.dto.AuthDtos.TokenRequest;
import com.electricwealth.api.dto.AuthDtos.TokenResponse;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.SessionRepository;
import com.electricwealth.api.repository.UserRepository;
import com.electricwealth.api.security.BearerTokens;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final UserRepository users;
    private final SessionRepository sessions;

    public AuthController(UserRepository users, SessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @PostMapping("/token")
    @Operation(summary = "Sign in with email and password",
            description = "Returns a session that still requires MFA (`mfaRequired: true`). Call `/api/auth/mfa/verify` next.")
    @ApiResponse(responseCode = "200", description = "Session created; MFA required")
    @ApiErrors({ 400, 401 })
    public TokenResponse token(@Valid @RequestBody TokenRequest body) {
        var user = users.findByEmail(body.username())
                .filter(u -> u.password().equals(body.password()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Invalid username or password."));
        var session = sessions.create(user.id(), false);
        return new TokenResponse(session.accessToken(), session.refreshToken(), "Bearer", TOKEN_TTL_SECONDS, true);
    }

    @PostMapping("/mfa/verify")
    @Operation(summary = "Complete MFA for the current session",
            description = "Send the access token from `/api/auth/token` as a Bearer token. The demo code is `123456`.")
    @ApiResponse(responseCode = "200", description = "Session is now MFA-verified")
    @ApiErrors({ 400, 401 })
    public MfaVerifyResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest body, HttpServletRequest request) {
        // Not behind the security filter: this token has not completed MFA yet, so it is checked here.
        var session = Optional.ofNullable(BearerTokens.from(request))
                .flatMap(sessions::findActive)
                .orElseThrow(ApiException::unauthorized);
        users.findById(session.userId())
                .filter(u -> u.mfaCode().equals(body.code()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_MFA", "Invalid MFA code."));
        var verified = sessions.markMfaVerified(session.accessToken()).orElseThrow(ApiException::unauthorized);
        return new MfaVerifyResponse(verified.accessToken(), TOKEN_TTL_SECONDS);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new session")
    @ApiResponse(responseCode = "200", description = "New tokens")
    @ApiErrors({ 400, 401 })
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest body) {
        var session = sessions.rotate(body.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH",
                        "Refresh token is invalid or expired."));
        return new RefreshResponse(session.accessToken(), session.refreshToken(), "Bearer", TOKEN_TTL_SECONDS);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current session")
    @ApiResponse(responseCode = "204", description = "Signed out")
    public void logout(@RequestBody(required = false) @Nullable LogoutRequest body, HttpServletRequest request) {
        sessions.revoke(BearerTokens.from(request), body != null ? body.refreshToken() : null);
    }
}
