package com.electricwealth.api.dto;

import java.util.List;

import com.electricwealth.api.dto.ApiEnums.AlertType;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** Response bodies for /api/wealth/dashboard (schemas/dashboard.ts in the Node API). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record PortfolioSummary(
            @Schema(description = "Sum of visible account balances (minor units)") long totalValue,
            String currency,
            long dayChange,
            double dayChangePct,
            String asOf) {
    }

    public record AllocationItem(@Schema(example = "Equity") String assetClass, double pct) {
    }

    public record DashboardGoalSummary(String id, String name, double progressPct) {
    }

    public record DashboardAlert(
            String id,
            AlertType type,
            String title,
            @Schema(example = "electricwealth://documents/d1") @Nullable String deepLink) {
    }

    public record QuickLink(String label, @Schema(example = "electricwealth://documents") String deepLink) {
    }

    public record DashboardResponse(
            PortfolioSummary portfolio,
            List<AllocationItem> allocation,
            List<DashboardGoalSummary> goals,
            List<DashboardAlert> alerts,
            List<QuickLink> quickLinks) {
    }
}
