package com.electricwealth.api.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * Converts between enums and their JSON values ({@code @JsonProperty}, or the constant name).
 * Jackson does this for request and response bodies; this class does the same for query parameters
 * and database columns, so {@code ?type=dividend} and a {@code 'dividend'} column both become
 * {@code TransactionType.DIVIDEND}.
 */
public final class JsonEnums {

    private static final Map<Enum<?>, String> VALUES = new ConcurrentHashMap<>();

    private JsonEnums() {
    }

    /** The JSON value of a constant, e.g. {@code "in_person"} for {@code MeetingType.IN_PERSON}. */
    public static String value(Enum<?> constant) {
        return VALUES.computeIfAbsent(constant, c -> {
            try {
                JsonProperty property = c.getDeclaringClass().getField(c.name()).getAnnotation(JsonProperty.class);
                return property != null ? property.value() : c.name();
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (value(constant).equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + value);
    }

    /** A Spring converter factory for String to any enum, by JSON value. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static final ConverterFactory<String, Enum> CONVERTER_FACTORY = new ConverterFactory<>() {
        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> source.isEmpty() ? null : (T) parse((Class) targetType, source);
        }
    };
}
