package com.electricwealth.api.web;

import java.util.Arrays;
import java.util.List;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.ApiEnums.HistoryRange;
import com.electricwealth.api.dto.MarketDtos.Basket;
import com.electricwealth.api.dto.MarketDtos.HistoryResponse;
import com.electricwealth.api.dto.MarketDtos.NewsItem;
import com.electricwealth.api.dto.MarketDtos.Quote;
import com.electricwealth.api.dto.MarketDtos.RoundupResponse;
import com.electricwealth.api.dto.MarketDtos.SearchResponse;
import com.electricwealth.api.dto.MarketDtos.SecurityDetail;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.MarketRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@Tag(name = "Market")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MarketController {

    private final MarketRepository market;

    public MarketController(MarketRepository market) {
        this.market = market;
    }

    private SecurityDetail securityOr404(String userId, String symbol) {
        return market.findSecurity(userId, symbol)
                .orElseThrow(() -> ApiException.notFound("Unknown symbol " + symbol + "."));
    }

    @GetMapping("/quote")
    @Operation(summary = "Quotes, with held and watched flags for the user")
    @ApiResponse(responseCode = "200", description = "Quotes, with held and watched flags for the user")
    @ApiErrors({ 401 })
    public List<Quote> quotes(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Comma-separated symbols. Omit for every listed security.", example = "XEQT.TO,AAPL")
            @RequestParam(required = false) @Nullable String symbols) {
        List<String> list = symbols == null ? null : Arrays.stream(symbols.split(","))
                .map(s -> s.strip().toUpperCase())
                .filter(s -> !s.isEmpty())
                .toList();
        return market.listQuotes(user.userId(), list);
    }

    @GetMapping("/securities/{symbol}")
    @Operation(summary = "One security with its quote and description")
    @ApiResponse(responseCode = "200", description = "One security with its quote and description")
    @ApiErrors({ 400, 401, 404 })
    public SecurityDetail security(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "XEQT.TO") @PathVariable String symbol) {
        return securityOr404(user.userId(), ApiParams.symbol(symbol, "symbol"));
    }

    @GetMapping("/history")
    @Operation(summary = "Closing prices over a range")
    @ApiResponse(responseCode = "200", description = "Closing prices over a range")
    @ApiErrors({ 400, 401, 404 })
    public HistoryResponse history(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "XEQT.TO") @RequestParam String symbol,
            @RequestParam(defaultValue = "1Y") HistoryRange range) {
        var security = securityOr404(user.userId(), ApiParams.symbol(symbol, "symbol"));
        return new HistoryResponse(security.symbol(), range, security.currency(),
                market.priceHistory(security.symbol(), range));
    }

    @GetMapping("/search")
    @Operation(summary = "Find securities by symbol or name")
    @ApiResponse(responseCode = "200", description = "Find securities by symbol or name")
    @ApiErrors({ 400, 401 })
    public SearchResponse search(@RequestParam(required = false) @Size(max = 40) @Nullable String q) {
        return new SearchResponse(market.search(q == null ? null : q.strip()));
    }

    @GetMapping("/news")
    @Operation(summary = "Market headlines, newest first")
    @ApiResponse(responseCode = "200", description = "Market headlines, newest first")
    @ApiErrors({ 401 })
    public List<NewsItem> news() {
        return market.listNews();
    }

    @GetMapping("/roundup")
    @Operation(summary = "This week's market roundup")
    @ApiResponse(responseCode = "200", description = "This week's market roundup")
    @ApiErrors({ 401 })
    public RoundupResponse roundup() {
        return market.latestRoundup();
    }

    @GetMapping("/baskets")
    @Operation(summary = "Thematic baskets")
    @ApiResponse(responseCode = "200", description = "Thematic baskets")
    @ApiErrors({ 401 })
    public List<Basket> baskets() {
        return market.listBaskets();
    }

    @GetMapping("/watchlist")
    @Operation(summary = "Quotes for the user's watchlist, in the order added")
    @ApiResponse(responseCode = "200", description = "Quotes for the user's watchlist, in the order added")
    @ApiErrors({ 401 })
    public List<Quote> watchlist(@AuthenticationPrincipal AuthenticatedUser user) {
        return market.listWatchlist(user.userId());
    }

    @PutMapping("/watchlist/{symbol}")
    @Operation(summary = "Add a security to the watchlist (no-op if already there)")
    @ApiResponse(responseCode = "200", description = "The updated watchlist")
    @ApiErrors({ 400, 401, 404 })
    public List<Quote> addToWatchlist(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "XEQT.TO") @PathVariable String symbol) {
        var security = securityOr404(user.userId(), ApiParams.symbol(symbol, "symbol"));
        market.addToWatchlist(user.userId(), security.symbol());
        return market.listWatchlist(user.userId());
    }

    @DeleteMapping("/watchlist/{symbol}")
    @Operation(summary = "Remove a security from the watchlist")
    @ApiResponse(responseCode = "200", description = "The updated watchlist")
    @ApiErrors({ 400, 401, 404 })
    public List<Quote> removeFromWatchlist(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "XEQT.TO") @PathVariable String symbol) {
        market.removeFromWatchlist(user.userId(), ApiParams.symbol(symbol, "symbol"));
        return market.listWatchlist(user.userId());
    }
}
