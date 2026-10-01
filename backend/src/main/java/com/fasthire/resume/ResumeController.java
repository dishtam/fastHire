package com.fasthire.resume;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResumeController {
    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    /** Generated when the user clicks Download: single-column, ATS-friendly PDF tailored to the job. */
    @GetMapping("/api/jobs/{id}/resume.pdf")
    public ResponseEntity<byte[]> resume(@PathVariable long id) {
        ResumeService.Pdf pdf = service.pdfForJob(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(pdf.filename()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(pdf.bytes());
    }
}
