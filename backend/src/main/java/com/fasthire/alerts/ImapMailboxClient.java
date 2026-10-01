package com.fasthire.alerts;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads a mailbox folder (e.g. a Gmail label holding the job alerts) over IMAP. Read-only: never changes mail. */
@Component
public class ImapMailboxClient implements MailboxClient {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String folder;
    private final boolean ssl;

    public ImapMailboxClient(@Value("${fasthire.mail.host:}") String host,
                             @Value("${fasthire.mail.port:993}") int port,
                             @Value("${fasthire.mail.username:}") String username,
                             @Value("${fasthire.mail.password:}") String password,
                             @Value("${fasthire.mail.folder:INBOX}") String folder,
                             @Value("${fasthire.mail.ssl:true}") boolean ssl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.folder = folder;
        this.ssl = ssl;
    }

    @Override
    public boolean configured() {
        return !host.isBlank() && !username.isBlank() && !password.isBlank();
    }

    @Override
    public List<EmailContent> fetchSince(Instant since) throws IOException {
        String protocol = ssl ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".connectiontimeout", "15000");
        props.put("mail." + protocol + ".timeout", "30000");
        Session session = Session.getInstance(props);
        List<EmailContent> out = new ArrayList<>();
        try (Store store = session.getStore(protocol)) {
            store.connect(host, port, username, password);
            Folder f = store.getFolder(folder);
            if (!f.exists()) {
                throw new IOException("Mail folder '" + folder + "' does not exist");
            }
            f.open(Folder.READ_ONLY);
            try {
                for (Message m : f.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(since)))) {
                    out.add(EmailExtractor.from((MimeMessage) m));
                }
            } finally {
                f.close(false);
            }
        } catch (MessagingException e) {
            throw new IOException("Could not read mailbox: " + e.getMessage(), e);
        }
        return out;
    }
}
