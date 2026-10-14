package com.electricwealth.api.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Conversions between SQLite's storage types and Java, matching what the Node API writes. */
public final class SqlValues {

    /** Same format as JavaScript's {@code Date.toISOString()}: always millisecond precision, UTC. */
    private static final DateTimeFormatter ISO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private SqlValues() {
    }

    public static String isoTimestamp(Instant instant) {
        return ISO_MILLIS.format(instant);
    }

    public static String isoNow() {
        return isoTimestamp(Instant.now());
    }

    /** Second precision, e.g. {@code 2026-10-14T17:49:35Z}, the format most tables use. */
    public static String isoSeconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static String isoNowSeconds() {
        return isoSeconds(Instant.now());
    }

    /** SQLite has no boolean type; columns store 0/1. */
    public static int toInt(boolean value) {
        return value ? 1 : 0;
    }

    /** Escapes {@code \ % _} so user input matches literally in {@code LIKE ? ESCAPE '\'}, wrapped in {@code %}. */
    public static String containsPattern(String text) {
        return "%" + (text == null ? "" : text.replaceAll("[\\\\%_]", "\\\\$0")) + "%";
    }

    /** Blank strings are stored as NULL, like the Node API's {@code value || null}. */
    public static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** 1-based paging to an SQL offset. */
    public static int offset(int page, int pageSize) {
        return (page - 1) * pageSize;
    }
}
