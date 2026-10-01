package com.fasthire.alerts;

import java.time.Instant;

/** The parts of an email the alert parsers need. {@code html} or {@code text} may be empty. */
public record EmailContent(String messageId, String from, String subject, String html, String text,
                           Instant receivedAt) {}
