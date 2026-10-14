package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks that every endpoint implemented here has the same contract as in the Node API's openapi.json:
 * the same paths and methods, status codes, parameters and request/response shapes (property names,
 * required fields, types and enum values). Descriptions, examples and formats are ignored. The web app's
 * TypeScript types are generated from the Node spec, so a match here means the web app works unchanged.
 * <p>
 * Node endpoints that are not ported yet are listed in the test output but don't fail the test.
 */
class ContractParityTest extends ApiTestBase {

    private static final Path NODE_SPEC = Path.of("../electric-wealth-api/openapi.json");
    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void implementedEndpointsMatchTheNodeApiContract() throws IOException {
        Assumptions.assumeTrue(Files.exists(NODE_SPEC), "electric-wealth-api is not cloned next to this repo");
        JsonNode node = mapper.readTree(NODE_SPEC.toFile());
        JsonNode spring = mapper.readTree(mvc.get().uri("/openapi.json").exchange().getResponse().getContentAsString());

        List<String> differences = new ArrayList<>();
        List<String> notPorted = new ArrayList<>();
        for (String path : fieldNames(node.get("paths"))) {
            for (String method : METHODS) {
                JsonNode nodeOp = node.get("paths").get(path).get(method);
                if (nodeOp == null) {
                    continue;
                }
                JsonNode springOp = spring.get("paths").has(path) ? spring.get("paths").get(path).get(method) : null;
                String op = method.toUpperCase() + " " + path;
                if (springOp == null) {
                    notPorted.add(op);
                    continue;
                }
                Object expected = operation(nodeOp, node);
                Object actual = operation(springOp, spring);
                if (!expected.equals(actual)) {
                    differences.add(op + "\n    node:   " + expected + "\n    spring: " + actual);
                }
            }
        }
        for (String path : fieldNames(spring.get("paths"))) {
            if (!node.get("paths").has(path)) {
                differences.add("Extra path not in the Node API: " + path);
            }
        }

        System.out.println("Contract parity: " + notPorted.size() + " Node endpoint(s) not ported yet"
                + (notPorted.isEmpty() ? "" : ":\n  " + String.join("\n  ", notPorted)));
        assertThat(differences).as("Contract differences from the Node API").isEmpty();
    }

    /** The parts of an operation that the web app depends on, in a comparable form. */
    private Map<String, Object> operation(JsonNode op, JsonNode root) {
        Map<String, Object> result = new TreeMap<>();
        Map<String, Object> params = new TreeMap<>();
        if (op.has("parameters")) {
            for (JsonNode p : op.get("parameters")) {
                // Header names are case-insensitive. A parameter can't be sent as JSON null, so "null" in its type
                // (Zod's coerced numbers produce one) doesn't matter.
                String name = p.get("in").asString().equals("header")
                        ? p.get("name").asString().toLowerCase()
                        : p.get("name").asString();
                params.put(p.get("in").asString() + ":" + name,
                        List.of(p.path("required").asBoolean(false), withoutNull(shape(p.get("schema"), root))));
            }
        }
        result.put("parameters", params);
        result.put("requestBody", op.has("requestBody")
                ? shape(op.get("requestBody").path("content").path("application/json").get("schema"), root)
                : "none");
        Map<String, Object> responses = new TreeMap<>();
        for (String status : fieldNames(op.get("responses"))) {
            JsonNode content = op.get("responses").get(status).get("content");
            responses.put(status, content == null ? "no content" : shape(firstSchema(content), root));
        }
        result.put("responses", responses);
        result.put("secured", op.has("security"));
        return result;
    }

    private static JsonNode firstSchema(JsonNode content) {
        for (JsonNode media : content) {
            return media.get("schema");
        }
        return null;
    }

    /** A schema reduced to what matters for compatibility, with $refs resolved. */
    private Object shape(JsonNode schema, JsonNode root) {
        if (schema == null || schema.isMissingNode()) {
            return "any";
        }
        if (schema.has("$ref")) {
            String name = schema.get("$ref").asString().substring("#/components/schemas/".length());
            return shape(root.get("components").get("schemas").get(name), root);
        }
        Map<String, Object> result = new TreeMap<>();
        if (schema.has("type")) {
            TreeSet<String> types = new TreeSet<>();
            if (schema.get("type").isArray()) {
                schema.get("type").forEach(t -> types.add(numeric(t.asString())));
            } else {
                types.add(numeric(schema.get("type").asString()));
            }
            result.put("type", types);
        }
        if (schema.has("enum")) {
            TreeSet<String> values = new TreeSet<>();
            schema.get("enum").forEach(v -> values.add(v.asString()));
            result.put("enum", values);
        }
        if (schema.has("properties")) {
            Map<String, Object> props = new TreeMap<>();
            for (String name : fieldNames(schema.get("properties"))) {
                props.put(name, shape(schema.get("properties").get(name), root));
            }
            result.put("properties", props);
            TreeSet<String> required = new TreeSet<>();
            schema.path("required").forEach(r -> required.add(r.asString()));
            result.put("required", required);
        }
        if (schema.has("items")) {
            result.put("items", shape(schema.get("items"), root));
        }
        for (String combinator : List.of("anyOf", "oneOf", "allOf")) {
            if (schema.has(combinator)) {
                List<Object> options = new ArrayList<>();
                schema.get(combinator).forEach(s -> options.add(shape(s, root)));
                // Zod wraps a $ref in allOf just to attach a description: [ref, {description}]. Unwrap it.
                options.removeIf(o -> o instanceof Map<?, ?> m && m.isEmpty());
                if (combinator.equals("allOf") && result.isEmpty()) {
                    // allOf of objects (Zod's .extend()) is one object with all the properties: merge them.
                    return options.size() == 1 ? options.getFirst() : mergeObjects(options);
                }
                result.put(combinator, options);
            }
        }
        if (schema.has("additionalProperties") && schema.get("additionalProperties").isObject()) {
            result.put("additionalProperties", shape(schema.get("additionalProperties"), root));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object mergeObjects(List<Object> shapes) {
        Map<String, Object> properties = new TreeMap<>();
        TreeSet<String> required = new TreeSet<>();
        for (Object shape : shapes) {
            var map = (Map<String, Object>) shape;
            properties.putAll((Map<String, Object>) map.getOrDefault("properties", Map.of()));
            required.addAll((TreeSet<String>) map.getOrDefault("required", new TreeSet<>()));
        }
        Map<String, Object> merged = new TreeMap<>();
        merged.put("type", new TreeSet<>(List.of("object")));
        merged.put("properties", properties);
        merged.put("required", required);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Object withoutNull(Object shape) {
        if (shape instanceof Map<?, ?> map && map.get("type") instanceof TreeSet<?> types && types.contains("null")) {
            var copy = new TreeMap<>((Map<String, Object>) map);
            var nonNull = new TreeSet<>((TreeSet<String>) types);
            nonNull.remove("null");
            copy.put("type", nonNull);
            return copy;
        }
        return shape;
    }

    /** Zod emits "number" for integers with an enum or literal, springdoc emits "integer": both are JSON numbers. */
    private static String numeric(String type) {
        return Objects.equals(type, "integer") ? "number" : type;
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node != null) {
            node.properties().forEach(e -> names.add(e.getKey()));
        }
        return names;
    }
}
