package com.electricwealth.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.electricwealth.api.dto.ApiEnums.GoalCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Request and response bodies for /api/wealth/goals (schemas/goals.ts in the Node API). */
public final class GoalDtos {

    private GoalDtos() {
    }

    /** $100M in cents. */
    public static final long MAX_AMOUNT = 100_000_000_00L;

    public record Goal(
            @Schema(example = "goal-1") String id,
            String name,
            GoalCategory category,
            long currentAmount,
            long targetAmount,
            String currency,
            @Schema(example = "2027-06-30") String targetDate,
            @Schema(description = "currentAmount / targetAmount, as a percentage (0-100)") double progressPct) {
    }

    public record GoalsListResponse(List<Goal> items, int page, int pageSize, long total) {
    }

    /** Also checked in the controller: targetDate is in the future and currentAmount doesn't exceed targetAmount. */
    public record GoalCreateRequest(
            @NotNull @Size(min = 1, max = 60) String name,
            @NotNull GoalCategory category,
            @NotNull @Positive @Max(MAX_AMOUNT) Long targetAmount,
            @PositiveOrZero @Max(MAX_AMOUNT) @Schema(description = "Defaults to 0") @Nullable Long currentAmount,
            @NotNull @Pattern(regexp = "CAD", message = "must be \"CAD\"") @Schema(allowableValues = "CAD") String currency,
            @NotNull @Schema(example = "2027-06-30") LocalDate targetDate) {

        public GoalCreateRequest {
            name = Text.trim(name);
        }
    }
}
