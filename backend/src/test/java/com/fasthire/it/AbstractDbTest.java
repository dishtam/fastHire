package com.fasthire.it;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real-Postgres tests. Uses a Testcontainers Postgres by default; set TEST_DB_URL (user/password
 * fasthire) to point at an existing empty database instead. Tables are truncated before each test.
 */
@SpringBootTest
public abstract class AbstractDbTest {
    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            r.add("spring.datasource.url", () -> url);
            r.add("spring.datasource.username", () -> "fasthire");
            r.add("spring.datasource.password", () -> "fasthire");
            return;
        }
        synchronized (AbstractDbTest.class) {
            if (container == null) {
                container = new PostgreSQLContainer<>("postgres:16");
                container.start();
            }
        }
        r.add("spring.datasource.url", container::getJdbcUrl);
        r.add("spring.datasource.username", container::getUsername);
        r.add("spring.datasource.password", container::getPassword);
    }

    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE draft_message, job_status, job_score, scrape_run, job, contact, source RESTART IDENTITY CASCADE");
    }

    protected long insertSource(String region, String url, String kind, String atsType, String token) {
        return jdbc.queryForObject("""
            INSERT INTO source (region, url, kind, ats_type, ats_token) VALUES (?, ?, ?, ?, ?) RETURNING id""",
            Long.class, region, url, kind, atsType, token);
    }

    protected long insertJob(long sourceId, String externalId, String title, String employer, String description) {
        return jdbc.queryForObject("""
            INSERT INTO job (source_id, external_id, employer_name, title, location, description, url, display_id)
            VALUES (?, ?, ?, ?, 'Dubai', ?, 'https://x/' || ?, ?) RETURNING id""",
            Long.class, sourceId, externalId, employer, title, description, externalId, externalId);
    }
}
