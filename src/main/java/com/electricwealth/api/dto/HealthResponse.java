package com.electricwealth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HealthResponse(@Schema(allowableValues = "ok") String status) {

    public static final HealthResponse OK = new HealthResponse("ok");
}
