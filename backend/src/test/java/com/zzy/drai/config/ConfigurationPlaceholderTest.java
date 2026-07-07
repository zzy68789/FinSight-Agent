package com.zzy.drai.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPlaceholderTest {

    @Test
    void applicationConfigUsesProjectScopedLlmEnvironmentVariables() throws IOException {
        String applicationYaml = readResource("/application.yml");

        assertThat(applicationYaml).contains("DRAI_LLM_API_KEY");
        assertThat(applicationYaml).contains("DRAI_LLM_BASE_URL");
        assertThat(applicationYaml).doesNotContain("OPENAI_API_KEY");
        assertThat(applicationYaml).doesNotContain("OPENAI_API_BASE");
    }

    private String readResource(String path) throws IOException {
        try (InputStream inputStream = ConfigurationPlaceholderTest.class.getResourceAsStream(path)) {
            assertThat(inputStream).as("resource %s exists", path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
