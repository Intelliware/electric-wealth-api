package com.electricwealth.api.web;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.DocumentDtos.DocumentDetailResponse;
import com.electricwealth.api.dto.DocumentDtos.DocumentShareRequest;
import com.electricwealth.api.dto.DocumentDtos.DocumentShareResponse;
import com.electricwealth.api.dto.DocumentDtos.DocumentsListResponse;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.DocumentRepository;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wealth/documents")
@Tag(name = "Documents")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DocumentController {

    private final DocumentRepository documents;

    public DocumentController(DocumentRepository documents) {
        this.documents = documents;
    }

    @GetMapping
    @Operation(summary = "List documents (tax slips, statements, confirmations, legal)")
    @ApiResponse(responseCode = "200", description = "Documents")
    @ApiErrors({ 400, 401 })
    public DocumentsListResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Nullable Integer year,
            @RequestParam(required = false) @Nullable String category) {
        var result = documents.list(user.userId(), page, pageSize, year, category);
        return new DocumentsListResponse(result.items(), page, pageSize, result.total());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Document download stub")
    @ApiResponse(responseCode = "200", description = "Document")
    @ApiErrors({ 401, 404 })
    public DocumentDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "d1") @PathVariable String id) {
        var doc = documents.find(user.userId(), id).orElseThrow(() -> ApiException.notFound("Document not found."));
        return new DocumentDetailResponse(doc.id(), doc.name(), doc.url(), "application/pdf",
                DocumentRepository.stubPdfBase64(doc.name()));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Share a document by email with a link that expires in 7 days")
    @ApiResponse(responseCode = "200", description = "Share created")
    @ApiErrors({ 400, 401, 404 })
    public DocumentShareResponse share(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(example = "d1") @PathVariable String id, @Valid @RequestBody DocumentShareRequest body) {
        documents.find(user.userId(), id).orElseThrow(() -> ApiException.notFound("Document not found."));
        String expiresAt = documents.share(user.userId(), id, body.recipient());
        return new DocumentShareResponse(body.recipient(), expiresAt);
    }
}
