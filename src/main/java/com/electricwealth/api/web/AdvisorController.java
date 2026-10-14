package com.electricwealth.api.web;

import java.util.List;
import java.util.function.Supplier;

import com.electricwealth.api.config.ApiErrors;
import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.config.OpenApiConfig;
import com.electricwealth.api.dto.AdvisorDtos.AdvisorInfo;
import com.electricwealth.api.dto.AdvisorDtos.AdvisorMessage;
import com.electricwealth.api.dto.AdvisorDtos.AdvisorMessageCreateRequest;
import com.electricwealth.api.dto.AdvisorDtos.Appointment;
import com.electricwealth.api.dto.AdvisorDtos.AppointmentCreateRequest;
import com.electricwealth.api.dto.AdvisorDtos.AppointmentPatchRequest;
import com.electricwealth.api.dto.AdvisorDtos.AvailabilityResponse;
import com.electricwealth.api.dto.ApiEnums.AppointmentStatus;
import com.electricwealth.api.dto.ApiEnums.MeetingType;
import com.electricwealth.api.error.ApiException;
import com.electricwealth.api.repository.AdvisorRepository;
import com.electricwealth.api.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
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
@RequestMapping("/api/advisor")
@Tag(name = "Advisor")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdvisorController {

    private final AdvisorRepository advisors;

    public AdvisorController(AdvisorRepository advisors) {
        this.advisors = advisors;
    }

    private AdvisorInfo advisorOr404(String userId) {
        return advisors.findUserAdvisor(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ADVISOR",
                "No advisor is assigned to this client."));
    }

    private static ApiException slotTaken() {
        return new ApiException(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE",
                "That time is no longer available. Please pick another slot.");
    }

    /**
     * Runs a booking change. The appointments table has a unique index on the advisor's scheduled slots, so if
     * someone else took the slot a moment ago the insert or update fails, and that becomes a 409.
     */
    private static <T> T bookingSlot(Supplier<T> change) {
        try {
            return change.get();
        } catch (DataAccessException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("UNIQUE constraint failed")) {
                throw slotTaken();
            }
            throw e;
        }
    }

    @GetMapping("/availability")
    @Operation(summary = "Open meeting slots with the user's advisor over the next five business days")
    @ApiResponse(responseCode = "200", description = "Availability")
    @ApiErrors({ 400, 401, 404 })
    public AvailabilityResponse availability(@AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Accepted for v1 compatibility; every type has the same slots")
            @RequestParam(required = false) @Nullable MeetingType meetingType) {
        return advisors.getAvailability(advisorOr404(user.userId()));
    }

    @GetMapping("/appointments")
    @Operation(summary = "The user's meetings (past and upcoming), oldest first")
    @ApiResponse(responseCode = "200", description = "Appointments")
    @ApiErrors({ 401 })
    public List<Appointment> appointments(@AuthenticationPrincipal AuthenticatedUser user) {
        return advisors.listAppointments(user.userId());
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a meeting in an open slot")
    @ApiResponse(responseCode = "201", description = "Booked")
    @ApiErrors({ 400, 401, 404, 409 })
    public Appointment book(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AppointmentCreateRequest body) {
        var advisor = advisorOr404(user.userId());
        if (!advisors.isSlotOpen(advisor, body.date(), body.time())) {
            throw slotTaken();
        }
        String id = bookingSlot(() -> advisors.createAppointment(user.userId(), advisor.id(), body));
        return advisors.findAppointment(user.userId(), id).orElseThrow();
    }

    @PatchMapping("/appointments/{id}")
    @Operation(summary = "Reschedule (new date and time) or cancel a scheduled meeting")
    @ApiResponse(responseCode = "200", description = "Updated appointment")
    @ApiErrors({ 400, 401, 404, 409 })
    public Appointment update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id,
            @Valid @RequestBody AppointmentPatchRequest body) {
        if ((body.date() == null) != (body.time() == null)) {
            throw ApiException.validation("time: date and time must be changed together");
        }
        if (body.date() == null && body.status() == null) {
            throw ApiException.validation("(root): Nothing to change");
        }
        if (body.date() != null && body.status() != null) {
            throw ApiException.validation("(root): Reschedule or cancel, not both");
        }
        var appointment = advisors.findAppointment(user.userId(), id)
                .orElseThrow(() -> ApiException.notFound("Appointment not found."));
        if (appointment.status() != AppointmentStatus.SCHEDULED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_SCHEDULED",
                    "A " + JsonEnums.value(appointment.status()) + " appointment cannot be changed.");
        }
        if (body.status() != null) {
            advisors.cancelAppointment(user.userId(), appointment.id());
        } else {
            var advisor = advisorOr404(user.userId());
            if (!advisors.isSlotOpen(advisor, body.date(), body.time())) {
                throw slotTaken();
            }
            bookingSlot(() -> {
                advisors.rescheduleAppointment(user.userId(), appointment.id(), body.date(), body.time());
                return null;
            });
        }
        return advisors.findAppointment(user.userId(), appointment.id()).orElseThrow();
    }

    @GetMapping("/messages")
    @Operation(summary = "Secure message thread with the advisor, oldest first")
    @ApiResponse(responseCode = "200", description = "Messages")
    @ApiErrors({ 401 })
    public List<AdvisorMessage> messages(@AuthenticationPrincipal AuthenticatedUser user) {
        return advisors.listMessages(user.userId());
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a message to the advisor")
    @ApiResponse(responseCode = "201", description = "Sent message")
    @ApiErrors({ 400, 401, 404 })
    public AdvisorMessage sendMessage(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AdvisorMessageCreateRequest body) {
        var advisor = advisorOr404(user.userId());
        return advisors.addClientMessage(user.userId(), advisor.id(), body.text());
    }
}
