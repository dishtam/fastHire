package com.fasthire.alerts;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/** Turns a MIME message into {@link EmailContent}: first HTML part, first plain-text part, headers. */
public final class EmailExtractor {
    private EmailExtractor() {}

    public static EmailContent from(MimeMessage m) throws MessagingException, IOException {
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        collect(m, html, text);

        Address[] from = m.getFrom();
        String sender = from == null || from.length == 0 ? "" : from[0] instanceof InternetAddress ia ? ia.getAddress() : from[0].toString();
        String subject = m.getSubject() == null ? "" : m.getSubject();
        Date sent = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
        Instant when = sent == null ? Instant.now() : sent.toInstant();
        String[] ids = m.getHeader("Message-ID");
        String id = ids != null && ids.length > 0 ? ids[0].trim() : sha1(sender + "|" + subject + "|" + when);
        return new EmailContent(id, sender, subject, html.toString(), text.toString(), when);
    }

    private static void collect(Part part, StringBuilder html, StringBuilder text) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            jakarta.mail.Multipart mp = (jakarta.mail.Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                collect(mp.getBodyPart(i), html, text);
            }
        } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
            // ignore attachments
        } else if (part.isMimeType("text/html") && html.isEmpty()) {
            html.append(part.getContent());
        } else if (part.isMimeType("text/plain") && text.isEmpty()) {
            text.append(part.getContent());
        }
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
