package com.electricwealth.api.repository;

import static com.electricwealth.api.repository.SqlValues.offset;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import com.electricwealth.api.dto.DocumentDtos.Document;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final Duration SHARE_TTL = Duration.ofDays(7);

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<Document> list(String userId, int page, int pageSize, @Nullable Integer year,
            @Nullable String category) {
        StringBuilder where = new StringBuilder("user_id = :userId");
        if (year != null) {
            where.append(" AND year = :year");
        }
        boolean hasCategory = category != null && !category.isEmpty();
        if (hasCategory) {
            where.append(" AND category = :category");
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM documents WHERE " + where)
                .param("userId", userId).param("year", year).param("category", category)
                .query(Long.class).single();
        List<Document> items = jdbc.sql("SELECT * FROM documents WHERE " + where
                + " ORDER BY year DESC, action_required DESC, id LIMIT :limit OFFSET :offset")
                .param("userId", userId).param("year", year).param("category", category)
                .param("limit", pageSize).param("offset", offset(page, pageSize))
                .query(Rows.of(Document.class)).list();
        return new PageResult<>(items, total);
    }

    public Optional<Document> find(String userId, String documentId) {
        return jdbc.sql("SELECT * FROM documents WHERE user_id = ? AND id = ?")
                .params(userId, documentId).query(Rows.of(Document.class)).optional();
    }

    /** Records a 7-day share of a document with someone by email. Returns the expiry time. */
    public String share(String userId, String documentId, String recipient) {
        Instant now = Instant.now();
        String expiresAt = SqlValues.isoSeconds(now.plus(SHARE_TTL));
        jdbc.sql("""
                INSERT INTO document_shares (user_id, document_id, recipient, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)""")
                .params(userId, documentId, recipient, SqlValues.isoSeconds(now), expiresAt)
                .update();
        return expiresAt;
    }

    /**
     * Builds a minimal one-page PDF showing the document name, base64-encoded. Stands in for real document
     * storage. Byte-for-byte the same as the Node API's {@code stubPdfBase64}.
     */
    public static String stubPdfBase64(String title) {
        String text = title.replaceAll("[^\\x20-\\x7e]", "?").replaceAll("[\\\\()]", "\\\\$0");
        String stream = "BT /F1 18 Tf 72 720 Td (" + text + ") Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> "
                        + "/Contents 4 0 R >>",
                "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        offsets.forEach(o -> pdf.append("%010d 00000 n \n".formatted(o)));
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return Base64.getEncoder().encodeToString(pdf.toString().getBytes(StandardCharsets.ISO_8859_1));
    }
}
