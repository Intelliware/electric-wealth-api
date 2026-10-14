package com.electricwealth.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

/** Request and response bodies for /api/wealth/documents (schemas/documents.ts in the Node API). */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    /** {@code accountId} is always sent, as {@code null} for documents that aren't about one account. */
    public record Document(
            @Schema(example = "d1") String id,
            @Schema(example = "2024 T5 Tax Slip") String name,
            @Schema(example = "Tax") String category,
            int year,
            @JsonInclude(JsonInclude.Include.ALWAYS) @Schema(types = { "string", "null" }) String accountId,
            @Nullable String accountLabel,
            long sizeBytes,
            boolean actionRequired,
            String url) {
    }

    public record DocumentsListResponse(List<Document> items, int page, int pageSize, long total) {
    }

    public record DocumentDetailResponse(String id, String name, String url, String contentType, String base64Stub) {
    }

    public record DocumentShareRequest(@NotBlank @Email @Schema(example = "accountant@example.com") String recipient) {
    }

    public record DocumentShareResponse(
            String recipient,
            @Schema(description = "The shared link stops working after this time (7 days)") String expiresAt) {
    }
}
