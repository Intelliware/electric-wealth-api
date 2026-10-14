package com.electricwealth.api.dto;

/** Input clean-up applied in request records' constructors, before validation runs. */
public final class Text {

    private Text() {
    }

    /** Trims surrounding whitespace, like Zod's {@code .trim()}. */
    public static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
