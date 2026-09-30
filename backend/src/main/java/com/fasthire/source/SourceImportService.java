package com.fasthire.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent import: re-importing an edited sheet updates rows instead of duplicating them. */
@Service
public class SourceImportService {

    public record ImportResult(int imported, List<String> errors) {}

    private final SheetParser parser;
    private final SourceClassifier classifier;
    private final JdbcTemplate jdbc;

    public SourceImportService(SheetParser parser, SourceClassifier classifier, JdbcTemplate jdbc) {
        this.parser = parser;
        this.classifier = classifier;
        this.jdbc = jdbc;
    }

    @Transactional
    public ImportResult importSheet(InputStream in) throws IOException {
        ParsedSheet sheet = parser.parse(in);
        for (SourceRow row : sheet.rows()) {
            Classification c = classifier.classify(row.url());
            jdbc.update("""
                INSERT INTO source (region, url, label, kind, ats_type, ats_token, enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (region, url) DO UPDATE SET
                  label = EXCLUDED.label, kind = EXCLUDED.kind, ats_type = EXCLUDED.ats_type,
                  ats_token = EXCLUDED.ats_token, enabled = EXCLUDED.enabled
                """,
                row.region(), row.url(), row.label(), c.kind().name(), c.atsType(), c.atsToken(),
                row.enabled());
        }
        return new ImportResult(sheet.rows().size(), sheet.errors());
    }
}
