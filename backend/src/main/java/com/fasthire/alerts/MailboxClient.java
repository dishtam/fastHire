package com.fasthire.alerts;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface MailboxClient {
    /** False when no mailbox is set up; ingestion is then skipped quietly. */
    boolean configured();

    /** Emails received at or after {@code since}. */
    List<EmailContent> fetchSince(Instant since) throws IOException;
}
