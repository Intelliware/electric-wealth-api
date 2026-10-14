package com.electricwealth.api.dto;

import java.time.LocalDate;
import java.util.List;

import com.electricwealth.api.dto.ApiEnums.AppointmentStatus;
import com.electricwealth.api.dto.ApiEnums.MeetingType;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Request and response bodies for /api/advisor (schemas/advisor.ts in the Node API). */
public final class AdvisorDtos {

    private AdvisorDtos() {
    }

    /** 24-hour start time, e.g. 14:30. */
    public static final String SLOT_TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    public record AdvisorInfo(String id, String name, @Nullable String designation, boolean available) {
    }

    public record AvailabilityDay(
            @Schema(example = "2027-06-30") String date,
            @Schema(description = "Open start times, Toronto time") List<String> slots) {
    }

    public record AvailabilityResponse(
            AdvisorInfo advisor,
            List<MeetingType> meetingTypes,
            @Schema(description = "The next five business days, starting tomorrow") List<AvailabilityDay> days) {
    }

    public record Appointment(
            String id,
            String advisorId,
            MeetingType meetingType,
            String date,
            @Schema(example = "14:30") String time,
            AppointmentStatus status,
            @Nullable String notes) {
    }

    public record AppointmentCreateRequest(
            @NotNull MeetingType meetingType,
            @NotNull @Schema(example = "2027-06-30") LocalDate date,
            @NotNull @Pattern(regexp = SLOT_TIME_PATTERN, message = "Must be HH:MM") @Schema(example = "14:30") String time,
            @Size(max = 500) @Nullable String notes) {

        public AppointmentCreateRequest {
            notes = Text.trim(notes);
        }
    }

    /** Reschedule (date and time together) or cancel. The combinations are checked in the controller. */
    public record AppointmentPatchRequest(
            @Schema(example = "2027-06-30") @Nullable LocalDate date,
            @Pattern(regexp = SLOT_TIME_PATTERN, message = "Must be HH:MM") @Schema(example = "14:30") @Nullable String time,
            @Pattern(regexp = "cancelled", message = "must be \"cancelled\"")
            @Schema(allowableValues = "cancelled") @Nullable String status) {
    }

    public enum MessageSender {
        @JsonProperty("advisor") ADVISOR,
        @JsonProperty("client") CLIENT,
    }

    public record AdvisorMessage(String id, MessageSender from, String text, String at) {
    }

    public record AdvisorMessageCreateRequest(@NotNull @Size(min = 1, max = 2000) String text) {

        public AdvisorMessageCreateRequest {
            text = Text.trim(text);
        }
    }
}
