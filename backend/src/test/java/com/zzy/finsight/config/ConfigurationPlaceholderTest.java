package com.zzy.finsight.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPlaceholderTest {

    @Test
    void applicationConfigOnlyUsesApiKeyEnvironmentVariables() throws IOException {
        String applicationYaml = readResource("/application.yml");

        assertThat(applicationYaml).contains("API_KEY");
        assertThat(applicationYaml).contains("TAVILY_API_KEY");
        assertThat(applicationYaml).contains("TUSHARE_API_KEY");
        List<String> placeholders = Pattern.compile("\\$\\{([^}:]+)")
                .matcher(applicationYaml)
                .results()
                .map(result -> result.group(1))
                .toList();
        assertThat(placeholders).containsExactlyInAnyOrder(
                "API_KEY",
                "TAVILY_API_KEY",
                "TUSHARE_API_KEY"
        );
        assertThat(applicationYaml).doesNotContain("OPENAI_API_KEY");
        assertThat(applicationYaml).doesNotContain("OPENAI_API_BASE");
    }

    @Test
    void applicationConfigUsesIsolatedFinSightStorageNames() throws IOException {
        String applicationYaml = readResource("/application.yml");

        assertThat(applicationYaml).contains("jdbc:mysql://localhost:3306/finsight");
        assertThat(applicationYaml).contains("database: 7");
        assertThat(applicationYaml).contains("collection: finsight_docs");
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
