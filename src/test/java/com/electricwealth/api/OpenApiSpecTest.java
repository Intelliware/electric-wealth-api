package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the committed openapi.json in sync with the code (the equivalent of {@code npm run check:openapi}).
 * Regenerate it with: {@code ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true}
 */
class OpenApiSpecTest extends ApiTestBase {

    private static final Path SPEC_FILE = Path.of("openapi.json");

    @Test
    void committedSpecIsUpToDate() throws IOException {
        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        JsonNode served = mapper.readTree(mvc.get().uri("/openapi.json").exchange().getResponse().getContentAsString());
        String generated = mapper.writeValueAsString(served) + "\n";

        if (Boolean.getBoolean("openapi.write")) {
            Files.writeString(SPEC_FILE, generated);
        }
        assertThat(SPEC_FILE).as("openapi.json is missing; run with -Dopenapi.write=true").exists();
        assertThat(Files.readString(SPEC_FILE))
                .as("openapi.json is out of date; run ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true")
                .isEqualTo(generated);
    }
}
