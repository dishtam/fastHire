package com.fasthire.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

/** Reads the India and UAE tabs. Bad rows are collected as errors, never fatal. */
@Component
public class SheetParser {

    private static final Map<String, String> TABS = Map.of("india", "IN", "uae", "UAE");
    private final DataFormatter fmt = new DataFormatter();

    public ParsedSheet parse(InputStream in) throws IOException {
        List<SourceRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            for (Sheet sheet : wb) {
                String region = TABS.get(sheet.getSheetName().trim().toLowerCase(Locale.ROOT));
                if (region == null) {
                    errors.add("Ignored tab '" + sheet.getSheetName() + "' (expected India or UAE)");
                    continue;
                }
                parseSheet(sheet, region, rows, errors);
            }
        }
        return new ParsedSheet(rows, errors);
    }

    private void parseSheet(Sheet sheet, String region, List<SourceRow> rows, List<String> errors) {
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            errors.add(sheet.getSheetName() + ": empty tab");
            return;
        }
        Map<String, Integer> cols = new HashMap<>();
        for (Cell c : header) {
            cols.put(fmt.formatCellValue(c).trim().toLowerCase(Locale.ROOT), c.getColumnIndex());
        }
        Integer urlCol = cols.get("url");
        if (urlCol == null) {
            errors.add(sheet.getSheetName() + ": missing 'url' header");
            return;
        }
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String url = text(row, urlCol);
            if (url.isEmpty()) {
                continue;
            }
            if (!url.contains(".")) {
                errors.add(sheet.getSheetName() + " row " + (r + 1) + ": not a URL: " + url);
                continue;
            }
            String enabled = cols.containsKey("enabled") ? text(row, cols.get("enabled")) : "";
            rows.add(new SourceRow(region, url,
                cols.containsKey("label") ? text(row, cols.get("label")) : "",
                !enabled.equalsIgnoreCase("false")));
        }
    }

    private String text(Row row, int col) {
        Cell c = row.getCell(col);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }
}
