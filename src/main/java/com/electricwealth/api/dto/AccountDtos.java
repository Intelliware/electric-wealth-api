package com.electricwealth.api.dto;

import java.util.List;

import com.electricwealth.api.dto.ApiEnums.AccountType;
import com.electricwealth.api.dto.ApiEnums.OpenableAccountType;
import com.electricwealth.api.dto.ApiEnums.PerformanceRange;
import com.electricwealth.api.dto.ApiEnums.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Request and response bodies for /api/wealth/accounts (schemas/accounts.ts in the Node API). */
public final class AccountDtos {

    private AccountDtos() {
    }

    /**
     * {@code nickname} is always sent, as {@code null} when there is none. {@code contributionRoom} is also sent
     * as {@code null} when it doesn't apply.
     */
    public record AccountSummary(
            @Schema(example = "tfsa") String id,
            AccountType type,
            @Schema(example = "Growth TFSA") String displayName,
            @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(types = { "string", "null" }) String nickname,
            @Schema(description = "Amount in minor units (cents)") long balance,
            @Schema(description = "ISO-4217 currency code", example = "CAD") String currency,
            boolean hidden,
            @Nullable Long dayChange,
            @Nullable Double dayChangePct,
            @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(types = { "integer", "null" }) @Nullable Long contributionRoom,
            @Schema(example = "Tax-free") String taxStatus,
            @Nullable Double rateOfReturn) {
    }

    public record AccountsListResponse(List<AccountSummary> items, int page, int pageSize, long total) {
    }

    public record Holding(
            @Schema(example = "XEQT.TO") String symbol,
            String name,
            double quantity,
            long marketValue,
            String currency,
            double weightPct) {
    }

    public record AccountDetail(
            String id,
            AccountType type,
            String displayName,
            @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(types = { "string", "null" }) String nickname,
            long balance,
            String currency,
            boolean hidden,
            @Nullable Long dayChange,
            @Nullable Double dayChangePct,
            @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(types = { "integer", "null" }) @Nullable Long contributionRoom,
            String taxStatus,
            @Nullable Double rateOfReturn,
            List<Holding> holdings) {

        public static AccountDetail of(AccountSummary a, List<Holding> holdings) {
            return new AccountDetail(a.id(), a.type(), a.displayName(), a.nickname(), a.balance(), a.currency(),
                    a.hidden(), a.dayChange(), a.dayChangePct(), a.contributionRoom(), a.taxStatus(),
                    a.rateOfReturn(), holdings);
        }
    }

    public record Transaction(
            String id,
            String date,
            String description,
            @Nullable String detail,
            TransactionType type,
            long amount,
            String currency) {
    }

    public record TransactionsResponse(
            List<Transaction> items, int page, int pageSize, long total, List<Transaction> scheduled) {
    }

    public record PerformancePoint(@Schema(description = "Period, e.g. 2024-03", example = "2024-03") String t,
            double v) {
    }

    public record Benchmark(String name, List<PerformancePoint> series) {
    }

    public record PerformanceResponse(
            PerformanceRange range,
            double timeWeightedReturnPct,
            String currency,
            List<PerformancePoint> series,
            Benchmark benchmark) {
    }

    /**
     * A class rather than a record so it can tell "nickname": null (clear the nickname) apart from no nickname
     * field at all (leave it unchanged).
     */
    public static final class AccountPatchRequest {

        private @Nullable String nickname;
        private boolean nicknameSent;
        private @Nullable Boolean hidden;

        @Size(max = 40)
        @Schema(types = { "string", "null" })
        public @Nullable String getNickname() {
            return nickname;
        }

        @JsonSetter("nickname")
        public void setNickname(@Nullable String nickname) {
            this.nickname = nickname;
            this.nicknameSent = true;
        }

        @JsonIgnore
        public boolean isNicknameSent() {
            return nicknameSent;
        }

        public @Nullable Boolean getHidden() {
            return hidden;
        }

        public void setHidden(@Nullable Boolean hidden) {
            this.hidden = hidden;
        }
    }

    public record AccountOpenRequest(
            @NotNull OpenableAccountType type,
            @NotNull @Size(min = 1, max = 40) @Schema(example = "Travel TFSA") String displayName,
            @Size(max = 40) @Nullable String nickname) {

        public AccountOpenRequest {
            displayName = Text.trim(displayName);
            nickname = Text.trim(nickname);
        }
    }
}
