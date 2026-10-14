package com.electricwealth.api.web;

import java.util.List;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.ApiEnums.EducationLevel;
import com.electricwealth.api.dto.ApiEnums.EducationType;
import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.dto.EducationDtos.EducationContent;
import com.electricwealth.api.dto.EducationDtos.EducationProgressRequest;
import com.electricwealth.api.dto.EducationDtos.EducationProgressResponse;
import com.electricwealth.api.dto.EducationDtos.GlossaryEntry;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.EducationRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/education")
@Tag(name = "Education")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class EducationController {

    private final EducationRepository education;

    public EducationController(EducationRepository education) {
        this.education = education;
    }

    @GetMapping("/content")
    @Operation(summary = "Learning centre catalogue with the user’s progress")
    @ApiResponse(responseCode = "200", description = "Classes and challenges")
    @ApiErrors({ 400, 401 })
    public List<EducationContent> content(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @Nullable EducationLevel level,
            @RequestParam(required = false) @Nullable EducationType type,
            @ApiParams.AcceptLanguage @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return education.listContent(user.userId(), Language.fromHeader(acceptLanguage), level, type);
    }

    @GetMapping("/glossary")
    @Operation(summary = "Financial glossary, A to Z, optionally filtered")
    @ApiResponse(responseCode = "200", description = "Glossary entries")
    @ApiErrors({ 400, 401 })
    public List<GlossaryEntry> glossary(
            @Parameter(description = "Matches the term or definition, case-insensitive")
            @RequestParam(required = false) @Size(max = 60) @Nullable String q,
            @ApiParams.AcceptLanguage @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return education.searchGlossary(Language.fromHeader(acceptLanguage), q == null ? null : q.strip());
    }

    @PostMapping("/progress")
    @Operation(summary = "Save progress on a class or challenge")
    @ApiResponse(responseCode = "200", description = "Saved progress")
    @ApiErrors({ 400, 401, 404 })
    public EducationProgressResponse saveProgress(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody EducationProgressRequest body) {
        if (!education.contentExists(body.contentId())) {
            throw ApiException.notFound("Content not found.");
        }
        education.saveProgress(user.userId(), body.contentId(), body.progressPct());
        return new EducationProgressResponse(body.contentId(), body.progressPct());
    }
}
