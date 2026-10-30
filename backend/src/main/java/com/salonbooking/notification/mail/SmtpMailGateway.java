package com.salonbooking.notification.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Production delivery through the SMTP server in {@code spring.mail.*}. */
class SmtpMailGateway implements MailGateway {

    private final JavaMailSender sender;

    SmtpMailGateway(JavaMailSender sender) {
        this.sender = sender;
    }

    @Override
    public String send(OutgoingEmail email) throws Exception {
        MimeMessage message = MimeMessages.build(email, sender.createMimeMessage());
        sender.send(message);
        return message.getMessageID();
    }
}
