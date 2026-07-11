package com.zzy.finsight.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPlaceholderTest {

    @Test
    void applicationConfigUsesProjectScopedLlmEnvironmentVariables() throws IOException {
        String applicationYaml = readResource("/application.yml");

        assertThat(applicationYaml).contains("FINSIGHT_LLM_API_KEY");
        assertThat(applicationYaml).contains("FINSIGHT_LLM_BASE_URL");
        assertThat(applicationYaml).doesNotContain("OPENAI_API_KEY");
        assertThat(applicationYaml).doesNotContain("OPENAI_API_BASE");
    }

    @Test
    void applicationConfigUsesIsolatedFinSightStorageNames() throws IOException {
        String applicationYaml = readResource("/application.yml");

        assertThat(applicationYaml).contains("FINSIGHT_DATASOURCE_URL");
        assertThat(applicationYaml).contains("jdbc:mysql://localhost:3306/finsight");
        assertThat(applicationYaml).contains("FINSIGHT_REDIS_DATABASE:7");
        assertThat(applicationYaml).contains("FINSIGHT_CHROMA_COLLECTION:finsight_docs");
        String retiredProjectIdentifier = String.join("", "d", "rai");
        assertThat(applicationYaml).doesNotContainIgnoringCase(retiredProjectIdentifier);
    }

    private String readResource(String path) throws IOException {
        try (InputStream inputStream = ConfigurationPlaceholderTest.class.getResourceAsStream(path)) {
            assertThat(inputStream).as("resource %s exists", path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
