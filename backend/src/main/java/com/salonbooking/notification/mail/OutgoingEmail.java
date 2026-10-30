package com.salonbooking.notification.mail;

import java.util.List;

/**
 * A rendered email. Sent from the platform's address with the business as sender name and the
 * branch as reply-to (NTF-7).
 */
public record OutgoingEmail(String fromName, String fromAddress, String replyTo, String to, String subject,
        String html, String text, List<Attachment> attachments) {

    public record Attachment(String filename, String contentType, byte[] content) {}
}
