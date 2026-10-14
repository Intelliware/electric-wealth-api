package com.electricwealth.api.error;

import io.swagger.v3.oas.annotations.media.Schema;

/** Every error response uses this shape: {@code { "error": { "code", "message", "traceId" } }}. */
public record ErrorEnvelope(Detail error) {

    public record Detail(@Schema(example = "NOT_FOUND") String code, String message, String traceId) {
    }

    public static ErrorEnvelope of(String code, String message, String traceId) {
        return new ErrorEnvelope(new Detail(code, message, traceId));
    }
}
