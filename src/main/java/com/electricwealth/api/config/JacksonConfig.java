package com.electricwealth.api.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

@Configuration
public class JacksonConfig {

    /**
     * Writes whole-number doubles without a trailing ".0" ({@code 48}, not {@code 48.0}), the way JavaScript's
     * JSON.stringify does, so percentages and other non-integer fields look the same as from the Node API.
     */
    @Bean
    JsonMapperBuilderCustomizer javascriptStyleNumbers() {
        var serializer = new StdSerializer<Double>(Double.class) {
            @Override
            public void serialize(Double value, JsonGenerator gen, SerializationContext ctxt) {
                double d = value;
                if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                    gen.writeNumber((long) d);
                } else {
                    gen.writeNumber(d);
                }
            }
        };
        var module = new SimpleModule("javascript-style-numbers")
                .addSerializer(Double.class, serializer)
                .addSerializer(double.class, serializer);
        return builder -> builder.addModule(module);
    }
}
