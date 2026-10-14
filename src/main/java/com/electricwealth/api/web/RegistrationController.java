package com.electricwealth.api.web;

import static com.electricwealth.api.repository.SessionRepository.TOKEN_TTL_SECONDS;

import java.util.List;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.dto.AuthDtos.TokenResponse;
import com.electricwealth.api.dto.RegistrationDtos.RegistrationCompleteRequest;
import com.electricwealth.api.dto.RegistrationDtos.RegistrationStartRequest;
import com.electricwealth.api.dto.RegistrationDtos.RegistrationStartResponse;
import com.electricwealth.api.dto.RegistrationDtos.SecurityAnswer;
import com.electricwealth.api.dto.RegistrationDtos.SecurityAnswersRequest;
import com.electricwealth.api.dto.RegistrationDtos.SecurityQuestion;
import com.electricwealth.api.dto.RegistrationDtos.StepSavedResponse;
import com.electricwealth.api.dto.RegistrationDtos.TrustedContactRequest;
import com.electricwealth.api.dto.RegistrationDtos.VerifyIdentityRequest;
import com.electricwealth.api.dto.RegistrationDtos.VerifyIdentityResponse;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.RegistrationRepository;
import com.electricwealth.api.repository.RegistrationRepository.RegistrationRow;
import com.electricwealth.api.repository.SessionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Online enrolment for an existing client, in steps. Public: no access token needed. */
@RestController
@RequestMapping("/api/registration")
@Tag(name = "Registration")
public class RegistrationController {

    private final RegistrationRepository registrations;
    private final SessionRepository sessions;

    public RegistrationController(RegistrationRepository registrations, SessionRepository sessions) {
        this.registrations = registrations;
        this.sessions = sessions;
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN",
                "This email already has online access. Sign in instead.");
    }

    private static ApiException outOfOrder(String message) {
        return new ApiException(HttpStatus.CONFLICT, "STEP_OUT_OF_ORDER", message);
    }

    /** The registration if it exists, has not expired and is not finished; otherwise 404. */
    private RegistrationRow openOr404(String id) {
        return registrations.findOpen(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "REGISTRATION_NOT_FOUND", "This registration has expired or does not exist. Please start again."));
    }

    /** Steps after identity verification need it done first. */
    private RegistrationRow verifiedOr409(String id) {
        RegistrationRow reg = openOr404(id);
        if (reg.accountNumber() == null) {
            throw outOfOrder("Verify your identity first.");
        }
        return reg;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Step 1: choose a login (email and password)",
            description = "Starts online enrolment for an existing client. 409 EMAIL_TAKEN if the email already has online access.")
    @ApiResponse(responseCode = "201", description = "Step 1: choose a login (email and password)")
    @ApiErrors({ 400, 409 })
    public RegistrationStartResponse start(@Valid @RequestBody RegistrationStartRequest body) {
        if (registrations.emailInUse(body.email())) {
            throw emailTaken();
        }
        return registrations.start(body.email(), body.password());
    }

    @PostMapping("/verify-identity")
    @Operation(summary = "Step 2: match the account number, postal code and date of birth on file",
            description = "422 IDENTITY_MISMATCH if no client record matches all three; 409 ALREADY_ENROLLED if that "
                    + "client already has online access.")
    @ApiResponse(responseCode = "200",
            description = "Step 2: match the account number, postal code and date of birth on file")
    @ApiErrors({ 400, 404, 409, 422 })
    public VerifyIdentityResponse verifyIdentity(@Valid @RequestBody VerifyIdentityRequest body) {
        RegistrationRow reg = openOr404(body.registrationId());
        var client = registrations.matchOfflineClient(body.accountNumber(), body.postalCode(), body.dateOfBirth())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IDENTITY_MISMATCH",
                        "We could not verify those details."));
        if (client.enrolledUserId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ENROLLED",
                    "This client already has online access. Sign in instead.");
        }
        registrations.attachOfflineClient(reg.id(), client.accountNumber());
        return new VerifyIdentityResponse(true, client.name());
    }

    @GetMapping("/security-questions")
    @Operation(summary = "Questions to choose from in step 3")
    @ApiResponse(responseCode = "200", description = "Security questions")
    public List<SecurityQuestion> securityQuestions(
            @ApiParams.AcceptLanguage @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return registrations.listSecurityQuestions(Language.fromHeader(acceptLanguage));
    }

    @PostMapping("/security-questions")
    @Operation(summary = "Step 3: answer two security questions")
    @ApiResponse(responseCode = "200", description = "Step 3: answer two security questions")
    @ApiErrors({ 400, 404, 409 })
    public StepSavedResponse saveSecurityAnswers(@Valid @RequestBody SecurityAnswersRequest body) {
        List<String> questionIds = body.answers().stream().map(SecurityAnswer::questionId).toList();
        if (questionIds.get(0).equals(questionIds.get(1))) {
            throw ApiException.validation("answers: Choose two different questions");
        }
        RegistrationRow reg = verifiedOr409(body.registrationId());
        List<String> unknown = registrations.unknownQuestionIds(questionIds);
        if (!unknown.isEmpty()) {
            throw ApiException.validation("Unknown question: " + String.join(", ", unknown));
        }
        registrations.saveSecurityAnswers(reg.id(), body.answers());
        return StepSavedResponse.SAVED;
    }

    @PostMapping("/trusted-contact")
    @Operation(summary = "Step 5 (optional): add a trusted contact")
    @ApiResponse(responseCode = "200", description = "Step 5 (optional): add a trusted contact")
    @ApiErrors({ 400, 404, 409 })
    public StepSavedResponse saveTrustedContact(@Valid @RequestBody TrustedContactRequest body) {
        RegistrationRow reg = verifiedOr409(body.registrationId());
        registrations.saveTrustedContact(reg.id(), body);
        return StepSavedResponse.SAVED;
    }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Final step: choose the code method, accept the terms and create the login",
            description = "Creates the user and returns a signed-in session (mfaRequired is false: the client just "
                    + "proved their identity).")
    @ApiResponse(responseCode = "201",
            description = "Final step: choose the code method, accept the terms and create the login")
    @ApiErrors({ 400, 404, 409 })
    public TokenResponse complete(@Valid @RequestBody RegistrationCompleteRequest body) {
        RegistrationRow reg = verifiedOr409(body.registrationId());
        if (!reg.securityAnswersSaved()) {
            throw outOfOrder("Answer your security questions first.");
        }
        if (registrations.emailInUse(reg.email())) {
            throw emailTaken();
        }
        String userId = registrations.complete(reg, body.mfaMethod());
        var session = sessions.create(userId, true);
        return new TokenResponse(session.accessToken(), session.refreshToken(), "Bearer", TOKEN_TTL_SECONDS, false);
    }
}
