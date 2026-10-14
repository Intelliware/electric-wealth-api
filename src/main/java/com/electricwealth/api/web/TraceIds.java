package com.electricwealth.api.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

/** Each request gets a trace id ({@code trace-xxxxxxxx}), returned in the X-Trace-Id header and in errors. */
public final class TraceIds {

    static final String ATTRIBUTE = TraceIds.class.getName();

    private TraceIds() {
    }

    static String newId() {
        return "trace-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String get(HttpServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        return id != null ? id.toString() : newId();
    }
}
