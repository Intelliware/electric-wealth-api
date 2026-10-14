package com.electricwealth.api.web;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.dto.DashboardDtos.DashboardResponse;
import com.electricwealth.api.dto.DashboardDtos.QuickLink;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.DashboardRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DashboardController {

    private final DashboardRepository dashboards;

    public DashboardController(DashboardRepository dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/api/wealth/dashboard")
    @Operation(summary = "Portfolio summary, allocation, goals, alerts and quick links")
    @ApiResponse(responseCode = "200", description = "Dashboard")
    @ApiErrors({ 401, 404 })
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthenticatedUser user,
            @ApiParams.AcceptLanguage @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        var dashboard = dashboards.find(user.userId())
                .orElseThrow(() -> ApiException.notFound("Dashboard not found."));
        Language language = Language.fromHeader(acceptLanguage);
        var quickLinks = dashboard.quickLinks().stream()
                .map(l -> new QuickLink(Labels.localize(l.label(), language), l.deepLink()))
                .toList();
        return new DashboardResponse(dashboard.portfolio(), dashboard.allocation(), dashboard.goals(),
                dashboard.alerts(), quickLinks);
    }
}
