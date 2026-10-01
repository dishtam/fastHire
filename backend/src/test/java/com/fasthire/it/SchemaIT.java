package com.fasthire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class SchemaIT extends AbstractDbTest {
    @Test
    void migrationsCreateTablesAndEnforceDedupKey() {
        Integer n = jdbc.queryForObject("""
            select count(*) from information_schema.tables
            where table_name in ('source','job','job_score','job_status','draft_message','scrape_run','contact')""",
            Integer.class);
        assertThat(n).isEqualTo(7);
        long s = insertSource("IN", "u", "ATS", "LEVER", "acme");
        insertJob(s, "1", "t", "e", "d");
        assertThatThrownBy(() -> insertJob(s, "1", "t2", "e", "d")).isInstanceOf(DuplicateKeyException.class);
    }
}
