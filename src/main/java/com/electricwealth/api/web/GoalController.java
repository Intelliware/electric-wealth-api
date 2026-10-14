package com.electricwealth.api.web;

import java.time.LocalDate;
import java.time.ZoneOffset;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.GoalDtos.Goal;
import com.electricwealth.api.dto.GoalDtos.GoalCreateRequest;
import com.electricwealth.api.dto.GoalDtos.GoalsListResponse;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.GoalRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wealth/goals")
@Tag(name = "Goals")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class GoalController {

    private final GoalRepository goals;

    public GoalController(GoalRepository goals) {
        this.goals = goals;
    }

    @GetMapping
    @Operation(summary = "List savings goals")
    @ApiResponse(responseCode = "200", description = "Goals")
    @ApiErrors({ 400, 401 })
    public GoalsListResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        var result = goals.list(user.userId(), page, pageSize);
        return new GoalsListResponse(result.items(), page, pageSize, result.total());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a savings goal")
    @ApiResponse(responseCode = "201", description = "Created goal")
    @ApiErrors({ 400, 401 })
    public Goal create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody GoalCreateRequest body) {
        // Compared with today's UTC date, as the Node API does.
        if (!body.targetDate().isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw ApiException.validation("targetDate: Must be a future date");
        }
        if (body.currentAmount() != null && body.currentAmount() > body.targetAmount()) {
            throw ApiException.validation("currentAmount: currentAmount cannot exceed targetAmount");
        }
        return goals.create(user.userId(), body);
    }
}
