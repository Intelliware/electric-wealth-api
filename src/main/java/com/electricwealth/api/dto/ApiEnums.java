package com.electricwealth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enums shared across the API (the equivalent of the enums in the Node API's schemas/common.ts).
 * {@code @JsonProperty} gives each constant its JSON value; the same value is stored in the database and
 * accepted in query parameters (see {@link com.electricwealth.api.config.JsonEnums}).
 */
public final class ApiEnums {

    private ApiEnums() {
    }

    @Schema(enumAsRef = true)
    public enum Language {
        @JsonProperty("en-CA") EN_CA,
        @JsonProperty("fr-CA") FR_CA;

        /** French when Accept-Language starts with "fr", otherwise English. */
        public static Language fromHeader(String acceptLanguage) {
            return acceptLanguage != null && acceptLanguage.startsWith("fr") ? FR_CA : EN_CA;
        }
    }

    @Schema(enumAsRef = true)
    public enum Theme {
        @JsonProperty("light") LIGHT,
        @JsonProperty("dark") DARK,
        @JsonProperty("system") SYSTEM,
    }

    @Schema(enumAsRef = true)
    public enum MeetingType {
        @JsonProperty("video") VIDEO,
        @JsonProperty("phone") PHONE,
        @JsonProperty("in_person") IN_PERSON,
    }

    @Schema(enumAsRef = true)
    public enum DocumentDelivery {
        @JsonProperty("digital") DIGITAL,
        @JsonProperty("paper_and_digital") PAPER_AND_DIGITAL,
    }

    @Schema(enumAsRef = true)
    public enum PerformanceRange {
        @JsonProperty("1M") ONE_MONTH,
        @JsonProperty("1Y") ONE_YEAR,
        @JsonProperty("ALL") ALL,
    }

    @Schema(enumAsRef = true)
    public enum TransactionType {
        @JsonProperty("contribution") CONTRIBUTION,
        @JsonProperty("buy") BUY,
        @JsonProperty("sell") SELL,
        @JsonProperty("dividend") DIVIDEND,
        @JsonProperty("fee") FEE,
        @JsonProperty("withdrawal") WITHDRAWAL,
        @JsonProperty("transfer") TRANSFER,
        @JsonProperty("scheduled") SCHEDULED,
    }

    @Schema(enumAsRef = true)
    public enum AccountType {
        TFSA, RRSP, RESP, RDSP, FHSA, NON_REGISTERED, LIRA, RRIF,
    }

    /** Account types a client can open online. The others are opened through an advisor. */
    @Schema(enumAsRef = true)
    public enum OpenableAccountType {
        TFSA, RRSP, FHSA, NON_REGISTERED,
    }

    @Schema(enumAsRef = true)
    public enum AlertType {
        ACTION_REQUIRED, INFO, SUCCESS, WARNING,
    }

    @Schema(enumAsRef = true)
    public enum NotificationType {
        ACTION_REQUIRED, INFO, SUCCESS, WARNING,
    }

    @Schema(enumAsRef = true)
    public enum MfaMethod {
        @JsonProperty("sms") SMS,
        @JsonProperty("email") EMAIL,
    }

    @Schema(enumAsRef = true)
    public enum GoalCategory {
        @JsonProperty("Education") EDUCATION,
        @JsonProperty("Vacation") VACATION,
        @JsonProperty("Home") HOME,
        @JsonProperty("Retirement") RETIREMENT,
        @JsonProperty("Safety") SAFETY,
    }

    @Schema(enumAsRef = true)
    public enum Province {
        AB, BC, MB, NB, NL, NS, NT, NU, ON, PE, QC, SK, YT,
    }

    @Schema(enumAsRef = true)
    public enum EducationLevel {
        @JsonProperty("beginner") BEGINNER,
        @JsonProperty("intermediate") INTERMEDIATE,
        @JsonProperty("advanced") ADVANCED,
    }

    @Schema(enumAsRef = true)
    public enum EducationType {
        @JsonProperty("class") CLASS,
        @JsonProperty("challenge") CHALLENGE,
    }

    @Schema(enumAsRef = true)
    public enum AppointmentStatus {
        @JsonProperty("scheduled") SCHEDULED,
        @JsonProperty("completed") COMPLETED,
        @JsonProperty("cancelled") CANCELLED,
    }

    @Schema(enumAsRef = true)
    public enum HistoryRange {
        @JsonProperty("1D") ONE_DAY,
        @JsonProperty("1M") ONE_MONTH,
        @JsonProperty("1Y") ONE_YEAR,
        @JsonProperty("5Y") FIVE_YEARS,
    }
}
