package com.fasthire.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SheetParserTest {

    private static void row(Sheet s, int i, String... v) {
        Row r = s.createRow(i);
        for (int c = 0; c < v.length; c++) {
            r.createCell(c).setCellValue(v[c]);
        }
    }

    @Test
    void parsesTabsSkipsBlanksAndReportsBadRows() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet in = wb.createSheet("India");
            row(in, 0, "url", "label", "enabled");
            row(in, 1, "https://jobs.lever.co/acme", "Acme", "");
            row(in, 2, "", "blank");
            row(in, 3, "notaurl", "bad");
            Sheet uae = wb.createSheet("uae");
            row(uae, 0, "URL");
            row(uae, 1, "www.naukri.com");
            row(wb.createSheet("Notes"), 0, "x");
            wb.write(out);
        }

        ParsedSheet p = new SheetParser().parse(new ByteArrayInputStream(out.toByteArray()));

        assertThat(p.rows()).extracting(SourceRow::region, SourceRow::url)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("IN", "https://jobs.lever.co/acme"),
                org.assertj.core.groups.Tuple.tuple("UAE", "www.naukri.com"));
        assertThat(p.rows().get(0).enabled()).isTrue();
        assertThat(p.errors()).hasSize(2);
    }
}
