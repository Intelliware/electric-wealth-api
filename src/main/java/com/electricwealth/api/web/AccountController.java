package com.electricwealth.api.web;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.AccountDtos.AccountDetail;
import com.electricwealth.api.dto.AccountDtos.AccountOpenRequest;
import com.electricwealth.api.dto.AccountDtos.AccountPatchRequest;
import com.electricwealth.api.dto.AccountDtos.AccountSummary;
import com.electricwealth.api.dto.AccountDtos.AccountsListResponse;
import com.electricwealth.api.dto.AccountDtos.Benchmark;
import com.electricwealth.api.dto.AccountDtos.PerformanceResponse;
import com.electricwealth.api.dto.AccountDtos.TransactionsResponse;
import com.electricwealth.api.dto.ApiEnums.PerformanceRange;
import com.electricwealth.api.dto.ApiEnums.TransactionType;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.AccountRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wealth/accounts")
@Tag(name = "Accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AccountController {

    private final AccountRepository accounts;

    public AccountController(AccountRepository accounts) {
        this.accounts = accounts;
    }

    private AccountSummary findOr404(String userId, String accountId) {
        return accounts.find(userId, accountId).orElseThrow(() -> ApiException.notFound("Account not found."));
    }

    @GetMapping
    @Operation(summary = "List visible accounts")
    @ApiResponse(responseCode = "200", description = "Accounts")
    @ApiErrors({ 400, 401 })
    public AccountsListResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        var result = accounts.listVisible(user.userId(), page, pageSize);
        return new AccountsListResponse(result.items(), page, pageSize, result.total());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new, empty account")
    @ApiResponse(responseCode = "201", description = "Opened account")
    @ApiErrors({ 400, 401 })
    public AccountSummary open(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AccountOpenRequest body) {
        String id = accounts.open(user.userId(), body);
        return findOr404(user.userId(), id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Account detail with holdings")
    @ApiResponse(responseCode = "200", description = "Account detail")
    @ApiErrors({ 401, 404 })
    public AccountDetail detail(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "tfsa") @PathVariable String id) {
        var account = findOr404(user.userId(), id);
        return AccountDetail.of(account, accounts.listHoldings(user.userId(), account.id()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an account nickname or visibility")
    @ApiResponse(responseCode = "200", description = "Updated account")
    @ApiErrors({ 400, 401, 404 })
    public AccountDetail update(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "tfsa") @PathVariable String id, @Valid @RequestBody AccountPatchRequest body) {
        findOr404(user.userId(), id);
        accounts.update(user.userId(), id, body);
        var account = findOr404(user.userId(), id);
        return AccountDetail.of(account, accounts.listHoldings(user.userId(), account.id()));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Posted transactions (paged) plus upcoming scheduled transactions")
    @ApiResponse(responseCode = "200", description = "Transactions")
    @ApiErrors({ 400, 401, 404 })
    public TransactionsResponse transactions(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "tfsa") @PathVariable String id,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Nullable TransactionType type) {
        findOr404(user.userId(), id);
        var result = accounts.listPostedTransactions(user.userId(), id, page, pageSize, type);
        return new TransactionsResponse(result.items(), page, pageSize, result.total(),
                accounts.listScheduledTransactions(user.userId(), id));
    }

    @GetMapping("/{id}/performance")
    @Operation(summary = "Account performance series with benchmark")
    @ApiResponse(responseCode = "200", description = "Performance")
    @ApiErrors({ 400, 401, 404 })
    public PerformanceResponse performance(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "tfsa") @PathVariable String id,
            @RequestParam(defaultValue = "1Y") PerformanceRange range) {
        var perf = accounts.findPerformance(user.userId(), id)
                .orElseThrow(() -> ApiException.notFound("Performance data not found."));
        return new PerformanceResponse(range, perf.timeWeightedReturnPct(), perf.currency(), perf.series(),
                new Benchmark(perf.benchmarkName(), perf.benchmarkSeries()));
    }
}
