package com.fasthire.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProfileLoaderTest {
    @Test
    void parsesExampleProfileIntoKeywordsAndPrompt() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of("../profile.example.yml"))) {
            Profile p = ProfileLoader.parse(in);
            assertThat(p.name()).isEqualTo("Alex Sample");
            assertThat(p.keywords()).contains("java", "spring boot", "kafka", "redis", "performance");
            assertThat(p.promptText()).contains("Spring Boot", "Cut API p95 latency", "Skills:");
        }
    }
}
