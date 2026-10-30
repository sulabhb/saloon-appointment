package com.salonbooking.notification.mail;

import jakarta.mail.internet.MimeMessage;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local development: writes each email to a folder instead of sending it. The .eml opens in any
 * mail app; the .html opens in a browser.
 */
class FileMailGateway implements MailGateway {

    private static final Logger log = LoggerFactory.getLogger(FileMailGateway.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path folder;
    private final Clock clock;

    FileMailGateway(Path folder, Clock clock) {
        this.folder = folder;
        this.clock = clock;
    }

    @Override
    public String send(OutgoingEmail email) throws Exception {
        Files.createDirectories(folder);
        String name = "%s-%s".formatted(STAMP.format(clock.instant()), email.to().replaceAll("[^A-Za-z0-9.@-]", "_"));
        MimeMessage message = MimeMessages.build(email);
        try (OutputStream out = Files.newOutputStream(folder.resolve(name + ".eml"))) {
            message.writeTo(out);
        }
        Files.writeString(folder.resolve(name + ".html"), email.html(), StandardCharsets.UTF_8);
        log.info("Email to {} written to {}.eml: {}", email.to(), folder.resolve(name).toAbsolutePath(), email.subject());
        return name;
    }
}
