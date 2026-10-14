package com.electricwealth.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Query parameters and path variables use the enums' JSON values, like request bodies do. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(JsonEnums.CONVERTER_FACTORY);
    }
}
