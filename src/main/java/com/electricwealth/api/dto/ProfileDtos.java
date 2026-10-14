package com.electricwealth.api.dto;

import com.electricwealth.api.dto.ApiEnums.DocumentDelivery;
import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.dto.ApiEnums.MeetingType;
import com.electricwealth.api.dto.ApiEnums.Province;
import com.electricwealth.api.dto.ApiEnums.Theme;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/** Request and response bodies for /api/profile and /api/preferences (schemas/profile.ts in the Node API). */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record Address(
            String line1,
            @Nullable String line2,
            String city,
            String province,
            String postalCode,
            String country) {
    }

    public record TrustedContact(String name, String relationship) {
    }

    public record ProfileResponse(
            String name,
            String email,
            String phoneMasked,
            String dateOfBirth,
            @Nullable String sinMasked,
            int memberSince,
            Address address,
            @Nullable TrustedContact trustedContact) {
    }

    public record ProfileAddressUpdate(@NotNull @Valid AddressInput address) {
    }

    /** Postal codes are accepted with or without the space and in any case, and stored as "A1A 1A1". */
    public record AddressInput(
            @NotNull @Size(min = 1, max = 100) String line1,
            @Size(max = 100) @Nullable String line2,
            @NotNull @Size(min = 1, max = 60) String city,
            @NotNull Province province,
            @NotNull @Pattern(regexp = "^[A-Za-z]\\d[A-Za-z] ?\\d[A-Za-z]\\d$",
                    message = "Must be a Canadian postal code, e.g. M5V 2A1")
            @Schema(example = "M5V 2A1") String postalCode,
            @NotNull @Pattern(regexp = "Canada", message = "must be \"Canada\"")
            @Schema(allowableValues = "Canada") String country) {

        public AddressInput {
            line1 = Text.trim(line1);
            line2 = Text.trim(line2);
            city = Text.trim(city);
            postalCode = Text.trim(postalCode);
        }

        /** The postal code in its stored form, e.g. "b2y4a1" becomes "B2Y 4A1". */
        public String normalizedPostalCode() {
            String compact = postalCode.replace(" ", "").toUpperCase();
            return compact.substring(0, 3) + " " + compact.substring(3);
        }
    }

    public record NotificationPrefs(boolean email, boolean sms, boolean push) {
    }

    public record PreferencesResponse(
            NotificationPrefs notifications,
            DocumentDelivery documentDelivery,
            Language language,
            Theme theme,
            MeetingType defaultMeetingType) {
    }

    /** Partial update: send only the fields to change. Notification flags can also be sent individually. */
    public record PreferencesUpdate(
            @Nullable NotificationPrefsUpdate notifications,
            @Nullable DocumentDelivery documentDelivery,
            @Nullable Language language,
            @Nullable Theme theme,
            @Nullable MeetingType defaultMeetingType) {
    }

    public record NotificationPrefsUpdate(@Nullable Boolean email, @Nullable Boolean sms, @Nullable Boolean push) {
    }
}
