package com.fasthire.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the real IMAP client against an embedded IMAP server. */
class ImapMailboxClientTest {
    private GreenMail greenMail;
    private int port;

    @BeforeEach
    void start() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        greenMail = new GreenMail(new ServerSetup(port, "127.0.0.1", "imap"));
        greenMail.start();
        GreenMailUser user = greenMail.setUser("me@example.com", "me", "secret");

        // saveChanges() would overwrite Message-ID with a generated one; a real mail server keeps the original.
        MimeMessage alert = new MimeMessage(Session.getInstance(new Properties())) {
            @Override protected void updateMessageID() { }
        };
        alert.setFrom(new InternetAddress("jobalerts@linkedin.example"));
        alert.setSubject("5 new Java jobs in Dubai");
        alert.setHeader("Message-ID", "<alert-1@example.com>");
        MimeMultipart alt = new MimeMultipart("alternative");
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("plain version");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<a href=\"https://www.linkedin.com/comm/jobs/view/3912345678\">Java Developer</a>", "text/html; charset=utf-8");
        alt.addBodyPart(plain);
        alt.addBodyPart(html);
        alert.setContent(alt);
        alert.saveChanges();
        user.deliver(alert);

        MimeMessage other = new MimeMessage(Session.getInstance(new Properties()));
        other.setFrom(new InternetAddress("friend@example.com"));
        other.setSubject("Lunch?");
        other.setText("hello");
        user.deliver(other);
    }

    @AfterEach
    void stop() {
        greenMail.stop();
    }

    private ImapMailboxClient client(String password, String folder) {
        return new ImapMailboxClient("127.0.0.1", port, "me", password, folder, false);
    }

    @Test void fetchesMessagesWithHtmlAndHeaders() throws Exception {
        ImapMailboxClient c = client("secret", "INBOX");
        assertThat(c.configured()).isTrue();

        List<EmailContent> emails = c.fetchSince(Instant.now().minus(1, ChronoUnit.DAYS));
        assertThat(emails).hasSize(2);
        EmailContent alert = emails.stream().filter(e -> e.subject().startsWith("5 new")).findFirst().orElseThrow();
        assertThat(alert.messageId()).isEqualTo("<alert-1@example.com>");
        assertThat(alert.from()).isEqualTo("jobalerts@linkedin.example");
        assertThat(alert.html()).contains("jobs/view/3912345678");
        assertThat(alert.text()).isEqualTo("plain version");
        assertThat(new AlertParser().parse(alert)).extracting(AlertJob::title).containsExactly("Java Developer");
    }

    @Test void messagesOlderThanTheWindowAreNotReturned() throws Exception {
        assertThat(client("secret", "INBOX").fetchSince(Instant.now().plus(3, ChronoUnit.DAYS))).isEmpty();
    }

    @Test void badPasswordAndMissingFolderGiveClearErrors() {
        assertThatThrownBy(() -> client("wrong", "INBOX").fetchSince(Instant.now().minus(1, ChronoUnit.DAYS)))
            .isInstanceOf(IOException.class).hasMessageContaining("Could not read mailbox");
        assertThatThrownBy(() -> client("secret", "NoSuchLabel").fetchSince(Instant.now().minus(1, ChronoUnit.DAYS)))
            .isInstanceOf(IOException.class).hasMessageContaining("NoSuchLabel");
    }

    @Test void unconfiguredWithoutCredentials() {
        assertThat(new ImapMailboxClient("", 993, "", "", "INBOX", true).configured()).isFalse();
    }
}
