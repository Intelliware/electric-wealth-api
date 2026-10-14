package com.electricwealth.api.repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.dto.ApiEnums.MfaMethod;
import com.electricwealth.api.dto.RegistrationDtos.RegistrationStartResponse;
import com.electricwealth.api.dto.RegistrationDtos.SecurityAnswer;
import com.electricwealth.api.dto.RegistrationDtos.SecurityQuestion;
import com.electricwealth.api.dto.RegistrationDtos.TrustedContactRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Online enrolment for existing (branch) clients: the {@code registrations} and {@code offline_clients} tables. */
@Repository
public class RegistrationRepository {

    private static final Duration REGISTRATION_TTL = Duration.ofMinutes(30);
    private static final String DEMO_MFA_CODE = "123456";

    public record RegistrationRow(String id, String email, String password, @Nullable String accountNumber,
            @Nullable String trustedContactName, @Nullable String trustedContactRelationship,
            boolean securityAnswersSaved, @Nullable String userId, String expiresAt) {
    }

    public record OfflineClientRow(String accountNumber, String name, String dateOfBirth, String phoneMasked,
            @Nullable String sinMasked, int clientSince, String addressLine1, @Nullable String addressLine2,
            String city, String province, String postalCode, String country, Language language,
            @Nullable String advisorId, @Nullable String enrolledUserId) {
    }

    private final JdbcClient jdbc;

    public RegistrationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String normalisePostalCode(String value) {
        return value.replaceAll("\\s", "").toUpperCase();
    }

    private static String normaliseAnswer(String value) {
        return value.strip().toLowerCase().replaceAll("\\s+", " ");
    }

    public boolean emailInUse(String email) {
        return jdbc.sql("SELECT 1 FROM users WHERE email = ?").param(email).query(Integer.class).optional().isPresent();
    }

    public RegistrationStartResponse start(String email, String password) {
        String id = "reg-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        String expiresAt = SqlValues.isoSeconds(now.plus(REGISTRATION_TTL));
        jdbc.sql("INSERT INTO registrations (id, email, password, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")
                .params(id, email, password, SqlValues.isoSeconds(now), expiresAt)
                .update();
        return new RegistrationStartResponse(id, expiresAt);
    }

    /** An open registration: exists, not expired and not yet completed. */
    public Optional<RegistrationRow> findOpen(String id) {
        String now = SqlValues.isoNowSeconds();
        return jdbc.sql("SELECT * FROM registrations WHERE id = ?")
                .param(id).query(Rows.of(RegistrationRow.class)).optional()
                .filter(r -> r.userId() == null && r.expiresAt().compareTo(now) > 0);
    }

    /** Finds the branch record matching all three identity details. */
    public Optional<OfflineClientRow> matchOfflineClient(String accountNumber, String postalCode, LocalDate dateOfBirth) {
        return findOfflineClient(accountNumber)
                .filter(c -> normalisePostalCode(c.postalCode()).equals(normalisePostalCode(postalCode))
                        && c.dateOfBirth().equals(dateOfBirth.toString()));
    }

    private Optional<OfflineClientRow> findOfflineClient(String accountNumber) {
        return jdbc.sql("SELECT * FROM offline_clients WHERE account_number = ?")
                .param(accountNumber).query(Rows.of(OfflineClientRow.class)).optional();
    }

    public void attachOfflineClient(String registrationId, String accountNumber) {
        jdbc.sql("UPDATE registrations SET account_number = ? WHERE id = ?").params(accountNumber, registrationId).update();
    }

    public List<SecurityQuestion> listSecurityQuestions(Language language) {
        String column = language == Language.FR_CA ? "text_fr" : "text_en";
        return jdbc.sql("SELECT id, " + column + " AS text FROM security_questions ORDER BY sort_order")
                .query(Rows.of(SecurityQuestion.class)).list();
    }

    public List<String> unknownQuestionIds(List<String> ids) {
        Set<String> known = new HashSet<>(jdbc.sql("SELECT id FROM security_questions").query(String.class).list());
        return ids.stream().filter(id -> !known.contains(id)).toList();
    }

    @Transactional
    public void saveSecurityAnswers(String registrationId, List<SecurityAnswer> answers) {
        jdbc.sql("DELETE FROM registration_security_answers WHERE registration_id = ?").param(registrationId).update();
        for (SecurityAnswer a : answers) {
            jdbc.sql("INSERT INTO registration_security_answers (registration_id, question_id, answer) VALUES (?, ?, ?)")
                    .params(registrationId, a.questionId(), normaliseAnswer(a.answer()))
                    .update();
        }
        jdbc.sql("UPDATE registrations SET security_answers_saved = 1 WHERE id = ?").param(registrationId).update();
    }

    public void saveTrustedContact(String registrationId, TrustedContactRequest c) {
        jdbc.sql("""
                UPDATE registrations
                SET trusted_contact_name = ?, trusted_contact_relationship = ?, trusted_contact_phone = ?,
                    trusted_contact_email = ?
                WHERE id = ?""")
                .params(c.name(), c.relationship(), SqlValues.blankToNull(c.phone()), SqlValues.blankToNull(c.email()),
                        registrationId)
                .update();
    }

    /**
     * Turns a finished registration into a user: login, profile (from the branch record), preferences,
     * advisor, security answers, an empty portfolio, starter quick links and a welcome notification.
     * Returns the new user id. Runs in one transaction, so a failure leaves nothing half-created.
     */
    @Transactional
    public String complete(RegistrationRow reg, MfaMethod mfaMethod) {
        OfflineClientRow client = findOfflineClient(reg.accountNumber()).orElseThrow();
        String userId = "u-" + UUID.randomUUID().toString().substring(0, 8);
        String now = SqlValues.isoNowSeconds();
        boolean french = client.language() == Language.FR_CA;

        jdbc.sql("""
                INSERT INTO users (id, email, password, mfa_code, name, account_number, date_of_birth, advisor_id,
                                   mfa_method, terms_accepted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(userId, reg.email(), reg.password(), DEMO_MFA_CODE, client.name(), client.accountNumber(),
                        client.dateOfBirth(), client.advisorId(), JsonEnums.value(mfaMethod), now)
                .update();
        jdbc.sql("""
                INSERT INTO user_profiles (user_id, phone_masked, sin_masked, member_since, address_line1, address_line2,
                                           city, province, postal_code, country, trusted_contact_name,
                                           trusted_contact_relationship)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(userId, client.phoneMasked(), client.sinMasked(), client.clientSince(), client.addressLine1(),
                        client.addressLine2(), client.city(), client.province(), client.postalCode(), client.country(),
                        reg.trustedContactName(), reg.trustedContactRelationship())
                .update();
        // Electronic delivery was consented to; SMS alerts follow the chosen code method.
        jdbc.sql("""
                INSERT INTO user_preferences (user_id, notify_email, notify_sms, notify_push, document_delivery, language,
                                              theme, default_meeting_type)
                VALUES (?, 1, ?, 0, 'digital', ?, 'system', 'video')""")
                .params(userId, SqlValues.toInt(mfaMethod == MfaMethod.SMS), JsonEnums.value(client.language()))
                .update();
        jdbc.sql("""
                INSERT INTO user_security_answers (user_id, question_id, answer)
                SELECT ?, question_id, answer FROM registration_security_answers WHERE registration_id = ?""")
                .params(userId, reg.id())
                .update();
        jdbc.sql("""
                INSERT INTO portfolio_snapshots (user_id, currency, day_change, day_change_pct, as_of)
                VALUES (?, 'CAD', 0, 0, ?)""")
                .params(userId, now)
                .update();
        jdbc.sql("""
                INSERT INTO notifications (user_id, id, title, body, type, created_at, read)
                VALUES (?, 'welcome', ?, ?, 'SUCCESS', ?, 0)""")
                .params(userId,
                        french ? "Bienvenue chez Electric Wealth" : "Welcome to Electric Wealth",
                        french ? "Votre accès en ligne est prêt. Ouvrez un compte ou écrivez à votre conseiller pour commencer."
                                : "Your online access is ready. Open an account or message your advisor to get started.",
                        now)
                .update();
        // Starter quick links for someone with no online history yet (labels are localised by the API).
        List<String[]> quickLinks = List.of(
                new String[] { "Open account", "electricwealth://accounts/new" },
                new String[] { "Book advisor", "electricwealth://advisor" },
                new String[] { "Learn basics", "electricwealth://learn" });
        for (int i = 0; i < quickLinks.size(); i++) {
            jdbc.sql("INSERT INTO quick_links (user_id, sort_order, label, deep_link) VALUES (?, ?, ?, ?)")
                    .params(userId, i + 1, quickLinks.get(i)[0], quickLinks.get(i)[1])
                    .update();
        }
        jdbc.sql("UPDATE offline_clients SET enrolled_user_id = ? WHERE account_number = ?")
                .params(userId, client.accountNumber()).update();
        jdbc.sql("UPDATE registrations SET mfa_method = ?, consented_at = ?, user_id = ? WHERE id = ?")
                .params(JsonEnums.value(mfaMethod), now, userId, reg.id()).update();
        return userId;
    }
}
