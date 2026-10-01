package com.fasthire.alerts;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AlertAdminController {
    public record Preview(String from, String subject, int jobs, List<AlertJob> parsed) {}

    private final AlertIngestService ingest;
    private final AlertParser parser;

    public AlertAdminController(AlertIngestService ingest, AlertParser parser) {
        this.ingest = ingest;
        this.parser = parser;
    }

    /** Reads new alert emails from the mailbox now (the daily pipeline also does this). */
    @PostMapping("/admin/alerts/ingest")
    public AlertIngestService.Result ingest() throws IOException {
        return ingest.ingest();
    }

    /**
     * Parses an uploaded .eml file and shows what would be extracted, without storing anything. Use it to check a
     * real alert email (and to share one when a layout is not recognised).
     */
    @PostMapping("/admin/alerts/preview")
    public Preview preview(@RequestParam("file") MultipartFile file) throws IOException {
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), file.getInputStream());
            EmailContent email = EmailExtractor.from(message);
            List<AlertJob> jobs = parser.parse(email);
            return new Preview(email.from(), email.subject(), jobs.size(), jobs);
        } catch (MessagingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a readable .eml file: " + e.getMessage());
        }
    }
}
