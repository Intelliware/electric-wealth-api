package com.electricwealth.api.config;

import java.lang.reflect.RecordComponent;
import java.util.Iterator;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Marks every field of a record as required in the OpenAPI spec unless it is annotated {@code @Nullable}.
 * That mirrors Zod, where fields are required unless marked {@code .optional()}, so the generated
 * TypeScript types in the web app come out the same.
 */
@Component
public class RequiredRecordFieldsConverter implements ModelConverter {

    @Override
    public Schema<?> resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema<?> resolved = chain.next().resolve(type, context, chain);
        Class<?> cls = Json.mapper().constructType(type.getType()).getRawClass();
        if (resolved == null || !cls.isRecord()) {
            return resolved;
        }
        Schema<?> model = resolved;
        String ref = resolved.get$ref();
        if (ref != null) {
            model = context.getDefinedModels().get(ref.substring(ref.lastIndexOf('/') + 1));
        }
        if (model == null || model.getProperties() == null) {
            return resolved;
        }
        for (RecordComponent component : cls.getRecordComponents()) {
            boolean optional = component.isAnnotationPresent(Nullable.class)
                    || component.getAnnotatedType().isAnnotationPresent(Nullable.class);
            String name = component.getName();
            if (!optional && model.getProperties().containsKey(name)
                    && (model.getRequired() == null || !model.getRequired().contains(name))) {
                model.addRequiredItem(name);
            }
        }
        return resolved;
    }
}
