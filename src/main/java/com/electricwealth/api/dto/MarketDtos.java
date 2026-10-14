package com.electricwealth.api.dto;

import java.util.List;

import com.electricwealth.api.dto.ApiEnums.HistoryRange;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

import org.jspecify.annotations.Nullable;

/** Response bodies for /api/market (schemas/market.ts in the Node API). */
public final class MarketDtos {

    private MarketDtos() {
    }

    public record Quote(
            String symbol,
            String name,
            String exchange,
            @Schema(description = "Last price, minor units of currency") long price,
            String currency,
            @Schema(description = "Change since previous close, minor units") long change,
            double changePct,
            @Schema(description = "The signed-in user holds this security in a visible account") boolean held,
            @Schema(description = "On the signed-in user's watchlist") boolean watched) {
    }

    public record SecurityDetail(
            String symbol,
            String name,
            String exchange,
            long price,
            String currency,
            long change,
            double changePct,
            boolean held,
            boolean watched,
            @Nullable String about) {
    }

    public record PricePoint(
            @Schema(description = "HH:MM for 1D, YYYY-MM-DD for 1M, YYYY-MM for 1Y and 5Y") String t,
            @Schema(description = "Close, minor units") long c) {
    }

    public record HistoryResponse(String symbol, HistoryRange range, String currency, List<PricePoint> series) {
    }

    public record SearchItem(String symbol, String name, String exchange, String currency) {
    }

    public record SearchResponse(List<SearchItem> items) {
    }

    public record NewsItem(String id, String title, String source, String publishedAt, @Nullable String tag) {
    }

    public enum Risk {
        @JsonProperty("Low") LOW,
        @JsonProperty("Medium") MEDIUM,
        @JsonProperty("High") HIGH,
    }

    public record Basket(String id, String name, String description, double return1YPct, int holdings, Risk risk) {
    }

    public record RoundupItem(String id, String title, String summary, String publishedAt) {
    }

    public record RoundupResponse(String weekOf, List<RoundupItem> items) {
    }
}
