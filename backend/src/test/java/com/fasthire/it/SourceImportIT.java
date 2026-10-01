package com.fasthire.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasthire.source.SourceImportService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SourceImportIT extends AbstractDbTest {
    @Autowired SourceImportService importer;

    private static byte[] workbook(String label) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet in = wb.createSheet("India");
            Row h = in.createRow(0);
            h.createCell(0).setCellValue("url");
            h.createCell(1).setCellValue("label");
            Row r1 = in.createRow(1);
            r1.createCell(0).setCellValue("https://jobs.lever.co/acme");
            r1.createCell(1).setCellValue(label);
            Row r2 = in.createRow(2);
            r2.createCell(0).setCellValue("www.naukri.com");
            Sheet uae = wb.createSheet("UAE");
            uae.createRow(0).createCell(0).setCellValue("url");
            uae.createRow(1).createCell(0).setCellValue("https://careers.custom.example/jobs");
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void importClassifiesAndIsIdempotent() throws Exception {
        var res = importer.importSheet(new ByteArrayInputStream(workbook("v1")));
        assertThat(res.imported()).isEqualTo(3);
        assertThat(res.errors()).isEmpty();

        List<Map<String, Object>> rows = jdbc.queryForList(
            "select region, url, label, kind, ats_type, ats_token from source order by id");
        assertThat(rows).extracting(r -> r.get("kind")).containsExactly("ATS", "BOARD_EMAIL_ONLY", "UNSUPPORTED");
        assertThat(rows.get(0)).containsEntry("ats_type", "LEVER").containsEntry("ats_token", "acme");

        importer.importSheet(new ByteArrayInputStream(workbook("v2")));
        assertThat(jdbc.queryForObject("select count(*) from source", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select label from source where ats_type='LEVER'", String.class))
            .isEqualTo("v2");
    }
}
