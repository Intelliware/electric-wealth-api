package com.electricwealth.api.web;

import com.electricwealth.api.dto.HealthResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Liveness check")
    @ApiResponse(responseCode = "200", description = "Service is up")
    public HealthResponse health() {
        return HealthResponse.OK;
    }
}
