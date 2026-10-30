package com.salonbooking.notification.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Builds a multipart (text + HTML + attachments) message from an {@link OutgoingEmail}. */
final class MimeMessages {

    private MimeMessages() {}

    static MimeMessage build(OutgoingEmail email, MimeMessage message) throws Exception {
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(email.fromAddress(), email.fromName(), StandardCharsets.UTF_8.name()));
        if (email.replyTo() != null) {
            helper.setReplyTo(email.replyTo());
        }
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        helper.setText(email.text(), email.html());
        for (OutgoingEmail.Attachment a : email.attachments()) {
            helper.addAttachment(a.filename(), new ByteArrayDataSource(a.content(), a.contentType()));
        }
        message.saveChanges();
        return message;
    }

    static MimeMessage build(OutgoingEmail email) throws Exception {
        return build(email, new MimeMessage(Session.getInstance(new Properties())));
    }
}
