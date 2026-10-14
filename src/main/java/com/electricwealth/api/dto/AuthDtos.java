package com.electricwealth.api.dto;

import com.electricwealth.api.dto.ApiEnums.MfaMethod;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;

/** Request and response bodies for /api/auth (schemas/auth.ts in the Node API). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record TokenRequest(
            @NotNull @Pattern(regexp = "password", message = "must be \"password\"")
            @Schema(allowableValues = "password") String grantType,
            @NotBlank @Email @Schema(example = "sarah.chen@example.com") String username,
            @NotEmpty @Schema(example = "Demo123!") String password) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            @Schema(allowableValues = "Bearer") String tokenType,
            @Schema(allowableValues = "900") int expiresIn,
            boolean mfaRequired) {
    }

    public record MfaVerifyRequest(
            @NotNull @Schema(description = "Opaque challenge id (the demo accepts any value)") String challengeId,
            @NotNull @Schema(example = "123456") String code,
            @NotNull MfaMethod method) {
    }

    public record MfaVerifyResponse(String accessToken, @Schema(allowableValues = "900") int expiresIn) {
    }

    public record RefreshRequest(@NotNull String refreshToken) {
    }

    public record RefreshResponse(
            String accessToken,
            String refreshToken,
            @Schema(allowableValues = "Bearer") String tokenType,
            @Schema(allowableValues = "900") int expiresIn) {
    }

    public record LogoutRequest(@Nullable String refreshToken) {
    }
}
