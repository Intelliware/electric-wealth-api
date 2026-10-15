package com.electricwealth.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.electricwealth.api.dto.ApiEnums.MfaMethod;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Request and response bodies for /api/registration (schemas/registration.ts in the Node API). */
public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record RegistrationStartRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 8, message = "At least 8 characters") @Size(max = 72)
            @Pattern(regexp = ".*[A-Za-z].*", message = "Must contain a letter")
            @Pattern(regexp = ".*\\d.*", message = "Must contain a number") String password) {
    }

    public record RegistrationStartResponse(
            @Schema(example = "reg-1a2b3c4d") String registrationId,
            @Schema(description = "Finish every step before this time (30 minutes)") String expiresAt) {
    }

    public record VerifyIdentityRequest(
            @NotNull @Schema(example = "reg-1a2b3c4d") String registrationId,
            @NotNull @Pattern(regexp = "^\\d{10}$", message = "Must be the 10-digit account number") String accountNumber,
            @NotNull @Size(max = 7) String postalCode,
            @NotNull @Schema(example = "2027-06-30") LocalDate dateOfBirth) {

        public VerifyIdentityRequest {
            accountNumber = Text.trim(accountNumber);
            postalCode = Text.trim(postalCode);
        }
    }

    public record VerifyIdentityResponse(
            boolean verified,
            @Schema(description = "The client name on file, to confirm the right record was found") String name) {
    }

    public record SecurityQuestion(String id, String text) {
    }

    /** Also checked in the controller: the two question ids are different and exist. */
    public record SecurityAnswersRequest(
            @NotNull @Schema(example = "reg-1a2b3c4d") String registrationId,
            @NotNull @Size(min = 2, max = 2, message = "Answer exactly two questions") List<@Valid SecurityAnswer> answers) {
    }

    public record SecurityAnswer(@NotNull String questionId, @NotNull @Size(min = 2, max = 100) String answer) {

        public SecurityAnswer {
            answer = Text.trim(answer);
        }
    }

    public record TrustedContactRequest(
            @NotNull @Schema(example = "reg-1a2b3c4d") String registrationId,
            @NotNull @Size(min = 1, max = 80) String name,
            @NotNull @Size(min = 1, max = 40) String relationship,
            @Size(max = 30) @Nullable String phone,
            @Email @Nullable String email) {

        public TrustedContactRequest {
            name = Text.trim(name);
            relationship = Text.trim(relationship);
            phone = Text.trim(phone);
        }
    }

    public record RegistrationCompleteRequest(
            @NotNull @Schema(example = "reg-1a2b3c4d") String registrationId,
            @NotNull @Schema(description = "Where one-time sign-in codes are sent") MfaMethod mfaMethod,
            @NotNull @Valid Consents consents) {
    }

    /** Every consent must be given (true). */
    public record Consents(
            @NotNull @AssertTrue Boolean terms,
            @NotNull @AssertTrue Boolean privacy,
            @NotNull @AssertTrue Boolean electronicDelivery) {
    }

    public record StepSavedResponse(boolean saved) {

        public static final StepSavedResponse SAVED = new StepSavedResponse(true);
    }
}
