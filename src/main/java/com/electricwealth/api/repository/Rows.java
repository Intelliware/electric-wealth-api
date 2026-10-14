package com.electricwealth.api.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.electricwealth.api.config.JsonEnums;

import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;

/**
 * Row mappers that fill a record from a query result by column name ({@code display_name} fills
 * {@code displayName}), converting 0/1 to booleans and enum columns by their JSON values.
 * Use {@code jdbc.sql(...).query(Rows.of(Foo.class))}.
 */
public final class Rows {

    private static final DefaultConversionService CONVERSIONS = new DefaultConversionService();
    private static final Map<Class<?>, RowMapper<?>> MAPPERS = new ConcurrentHashMap<>();

    static {
        CONVERSIONS.addConverterFactory(JsonEnums.CONVERTER_FACTORY);
    }

    private Rows() {
    }

    @SuppressWarnings("unchecked")
    public static <T> RowMapper<T> of(Class<T> type) {
        return (RowMapper<T>) MAPPERS.computeIfAbsent(type, t -> {
            var mapper = new DataClassRowMapper<>(t);
            mapper.setConversionService(CONVERSIONS);
            return mapper;
        });
    }
}
