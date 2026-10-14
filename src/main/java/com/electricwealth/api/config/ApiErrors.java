package com.electricwealth.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents the error responses an endpoint can return, all using the ErrorEnvelope shape
 * (the equivalent of {@code errors(400, 401)} in the Node API). {@link OpenApiConfig} reads it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {

    int[] value();
}
