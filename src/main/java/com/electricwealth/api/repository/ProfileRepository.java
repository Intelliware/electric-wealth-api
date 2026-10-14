package com.electricwealth.api.repository;

import static com.electricwealth.api.repository.SqlValues.toInt;

import java.util.Optional;

import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.dto.ApiEnums.DocumentDelivery;
import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.dto.ApiEnums.MeetingType;
import com.electricwealth.api.dto.ApiEnums.Theme;
import com.electricwealth.api.dto.ProfileDtos.Address;
import com.electricwealth.api.dto.ProfileDtos.AddressInput;
import com.electricwealth.api.dto.ProfileDtos.NotificationPrefs;
import com.electricwealth.api.dto.ProfileDtos.PreferencesResponse;
import com.electricwealth.api.dto.ProfileDtos.PreferencesUpdate;
import com.electricwealth.api.dto.ProfileDtos.ProfileResponse;
import com.electricwealth.api.dto.ProfileDtos.TrustedContact;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProfileRepository {

    private record ProfileRow(String name, String email, String dateOfBirth, String phoneMasked,
            @Nullable String sinMasked, int memberSince, String addressLine1, @Nullable String addressLine2,
            String city, String province, String postalCode, String country, @Nullable String trustedContactName,
            @Nullable String trustedContactRelationship) {
    }

    private record PreferencesRow(boolean notifyEmail, boolean notifySms, boolean notifyPush,
            DocumentDelivery documentDelivery, Language language, Theme theme, MeetingType defaultMeetingType) {
    }

    private final JdbcClient jdbc;

    public ProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProfileResponse> findProfile(String userId) {
        return jdbc.sql("""
                SELECT u.name, u.email, u.date_of_birth, p.*
                FROM users u JOIN user_profiles p ON p.user_id = u.id
                WHERE u.id = ?""")
                .param(userId).query(Rows.of(ProfileRow.class)).optional()
                .map(r -> new ProfileResponse(r.name(), r.email(), r.phoneMasked(), r.dateOfBirth(), r.sinMasked(),
                        r.memberSince(),
                        new Address(r.addressLine1(), r.addressLine2(), r.city(), r.province(), r.postalCode(),
                                r.country()),
                        r.trustedContactName() != null && r.trustedContactRelationship() != null
                                ? new TrustedContact(r.trustedContactName(), r.trustedContactRelationship())
                                : null));
    }

    public void updateAddress(String userId, AddressInput address) {
        jdbc.sql("""
                UPDATE user_profiles
                SET address_line1 = ?, address_line2 = ?, city = ?, province = ?, postal_code = ?, country = ?
                WHERE user_id = ?""")
                .params(address.line1(), SqlValues.blankToNull(address.line2()), address.city(),
                        JsonEnums.value(address.province()), address.normalizedPostalCode(), address.country(), userId)
                .update();
    }

    public Optional<PreferencesResponse> findPreferences(String userId) {
        return jdbc.sql("SELECT * FROM user_preferences WHERE user_id = ?")
                .param(userId).query(Rows.of(PreferencesRow.class)).optional()
                .map(r -> new PreferencesResponse(new NotificationPrefs(r.notifyEmail(), r.notifySms(), r.notifyPush()),
                        r.documentDelivery(), r.language(), r.theme(), r.defaultMeetingType()));
    }

    /** Applies only the fields present in the update; the rest keep their current values. */
    @Transactional
    public void updatePreferences(String userId, PreferencesUpdate update) {
        findPreferences(userId).ifPresent(current -> {
            var n = update.notifications();
            boolean email = n != null && n.email() != null ? n.email() : current.notifications().email();
            boolean sms = n != null && n.sms() != null ? n.sms() : current.notifications().sms();
            boolean push = n != null && n.push() != null ? n.push() : current.notifications().push();
            jdbc.sql("""
                    UPDATE user_preferences
                    SET notify_email = ?, notify_sms = ?, notify_push = ?, document_delivery = ?,
                        language = ?, theme = ?, default_meeting_type = ?
                    WHERE user_id = ?""")
                    .params(toInt(email), toInt(sms), toInt(push),
                            JsonEnums.value(orElse(update.documentDelivery(), current.documentDelivery())),
                            JsonEnums.value(orElse(update.language(), current.language())),
                            JsonEnums.value(orElse(update.theme(), current.theme())),
                            JsonEnums.value(orElse(update.defaultMeetingType(), current.defaultMeetingType())),
                            userId)
                    .update();
        });
    }

    private static <T> T orElse(@Nullable T value, T fallback) {
        return value != null ? value : fallback;
    }
}
