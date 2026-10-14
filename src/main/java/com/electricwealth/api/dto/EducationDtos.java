package com.electricwealth.api.dto;

import com.electricwealth.api.dto.ApiEnums.EducationLevel;
import com.electricwealth.api.dto.ApiEnums.EducationType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request and response bodies for /api/education (schemas/education.ts in the Node API). */
public final class EducationDtos {

    private EducationDtos() {
    }

    public record EducationContent(
            @Schema(example = "e1") String id,
            @Schema(description = "In the language from Accept-Language") String title,
            EducationLevel level,
            EducationType type,
            int durationMin,
            int progressPct) {
    }

    public record GlossaryEntry(String term, String definition) {
    }

    public record EducationProgressRequest(@NotNull String contentId, @NotNull @Min(0) @Max(100) Integer progressPct) {
    }

    public record EducationProgressResponse(String contentId, int progressPct) {
    }
}
