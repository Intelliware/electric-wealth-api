package com.electricwealth.api.repository;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.dto.ApiEnums.EducationLevel;
import com.electricwealth.api.dto.ApiEnums.EducationType;
import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.dto.EducationDtos.EducationContent;
import com.electricwealth.api.dto.EducationDtos.GlossaryEntry;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EducationRepository {

    private final JdbcClient jdbc;

    public EducationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Column suffix for the language: titles and glossary terms are stored as {@code _en} and {@code _fr}. */
    private static String suffix(Language language) {
        return language == Language.FR_CA ? "fr" : "en";
    }

    public List<EducationContent> listContent(String userId, Language language, @Nullable EducationLevel level,
            @Nullable EducationType type) {
        StringBuilder where = new StringBuilder("1 = 1");
        if (level != null) {
            where.append(" AND c.level = :level");
        }
        if (type != null) {
            where.append(" AND c.type = :type");
        }
        return jdbc.sql("""
                SELECT c.id, c.title_%s AS title, c.level, c.type, c.duration_min,
                       COALESCE(p.progress_pct, 0) AS progress_pct
                FROM education_content c
                LEFT JOIN education_progress p ON p.content_id = c.id AND p.user_id = :userId
                WHERE %s
                ORDER BY c.sort_order, c.id""".formatted(suffix(language), where))
                .param("userId", userId)
                .param("level", level != null ? JsonEnums.value(level) : null)
                .param("type", type != null ? JsonEnums.value(type) : null)
                .query(Rows.of(EducationContent.class)).list();
    }

    public boolean contentExists(String contentId) {
        return jdbc.sql("SELECT 1 FROM education_content WHERE id = ?").param(contentId)
                .query(Integer.class).optional().isPresent();
    }

    public void saveProgress(String userId, String contentId, int progressPct) {
        jdbc.sql("""
                INSERT INTO education_progress (user_id, content_id, progress_pct, updated_at)
                VALUES (?, ?, ?, strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                ON CONFLICT (user_id, content_id)
                DO UPDATE SET progress_pct = excluded.progress_pct, updated_at = excluded.updated_at""")
                .params(userId, contentId, progressPct).update();
    }

    public List<GlossaryEntry> searchGlossary(Language language, @Nullable String q) {
        String s = suffix(language);
        String like = SqlValues.containsPattern(q);
        List<GlossaryEntry> rows = new ArrayList<>(jdbc.sql("""
                SELECT term_%1$s AS term, definition_%1$s AS definition
                FROM glossary_terms
                WHERE term_%1$s LIKE ? ESCAPE '\\' OR definition_%1$s LIKE ? ESCAPE '\\'""".formatted(s))
                .params(like, like).query(Rows.of(GlossaryEntry.class)).list());
        // Sorted in code: SQLite's NOCASE ordering puts accented letters (é) after z.
        Collator collator = Collator.getInstance(language == Language.FR_CA ? Locale.CANADA_FRENCH : Locale.CANADA);
        collator.setStrength(Collator.PRIMARY);
        rows.sort((a, b) -> collator.compare(a.term(), b.term()));
        return rows;
    }
}
