package com.electricwealth.api.web;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.ProfileDtos.PreferencesResponse;
import com.electricwealth.api.dto.ProfileDtos.PreferencesUpdate;
import com.electricwealth.api.dto.ProfileDtos.ProfileAddressUpdate;
import com.electricwealth.api.dto.ProfileDtos.ProfileResponse;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.ProfileRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's profile (/api/profile) and preferences (/api/preferences). */
@RestController
@Tag(name = "Profile")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProfileController {

    private final ProfileRepository profiles;

    public ProfileController(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    private ProfileResponse profileOr404(String userId) {
        return profiles.findProfile(userId).orElseThrow(() -> ApiException.notFound("Profile not found."));
    }

    private PreferencesResponse preferencesOr404(String userId) {
        return profiles.findPreferences(userId).orElseThrow(() -> ApiException.notFound("Preferences not found."));
    }

    @GetMapping("/api/profile")
    @Operation(summary = "Signed-in user profile")
    @ApiResponse(responseCode = "200", description = "Profile")
    @ApiErrors({ 401, 404 })
    public ProfileResponse profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return profileOr404(user.userId());
    }

    @PutMapping("/api/profile/address")
    @Operation(summary = "Replace the mailing address")
    @ApiResponse(responseCode = "200", description = "Updated profile")
    @ApiErrors({ 400, 401, 404 })
    public ProfileResponse updateAddress(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProfileAddressUpdate body) {
        profiles.updateAddress(user.userId(), body.address());
        return profileOr404(user.userId());
    }

    @GetMapping("/api/preferences")
    @Operation(summary = "Signed-in user preferences")
    @ApiResponse(responseCode = "200", description = "Preferences")
    @ApiErrors({ 401, 404 })
    public PreferencesResponse preferences(@AuthenticationPrincipal AuthenticatedUser user) {
        return preferencesOr404(user.userId());
    }

    @PutMapping("/api/preferences")
    @Operation(summary = "Update preferences (partial update)")
    @ApiResponse(responseCode = "200", description = "Updated preferences")
    @ApiErrors({ 400, 401, 404 })
    public PreferencesResponse updatePreferences(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PreferencesUpdate body) {
        profiles.updatePreferences(user.userId(), body);
        return preferencesOr404(user.userId());
    }
}
