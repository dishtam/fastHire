package com.fasthire.source;

import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SourceAdminController {

    private final SourceImportService importer;

    public SourceAdminController(SourceImportService importer) {
        this.importer = importer;
    }

    @PostMapping("/admin/sources/import")
    public SourceImportService.ImportResult upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        return importer.importSheet(file.getInputStream());
    }
}
