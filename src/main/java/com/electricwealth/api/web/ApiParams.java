package com.electricwealth.api.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

import com.electricwealth.api.dto.ApiEnums.Language;
import com.electricwealth.api.error.ApiException;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;

/** Request parameters shared by several controllers. */
final class ApiParams {

    private static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9.]{1,12}$");

    private ApiParams() {
    }

    /**
     * Documents the optional Accept-Language header as {@code en-CA | fr-CA}. Put it on a
     * {@code @RequestHeader(value = "Accept-Language", required = false) String} parameter and read it with
     * {@link Language#fromHeader}.
     */
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @Parameter(name = "accept-language", in = ParameterIn.HEADER, schema = @Schema(implementation = Language.class))
    @interface AcceptLanguage {
    }

    /** A security symbol, trimmed and upper-cased: "xeqt.to" becomes "XEQT.TO". 400 if it isn't valid. */
    static String symbol(String raw, String field) {
        String symbol = raw == null ? "" : raw.strip().toUpperCase();
        if (!SYMBOL.matcher(symbol).matches()) {
            throw ApiException.validation(field + ": Not a valid symbol");
        }
        return symbol;
    }
}
