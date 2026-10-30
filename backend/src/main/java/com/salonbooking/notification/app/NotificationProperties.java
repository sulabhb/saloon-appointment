package com.salonbooking.notification.app;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Customer notifications ({@code salon.notifications.*}).
 *
 * @param delivery {@code smtp} sends through {@code spring.mail.*}; {@code file} writes each email
 *     to {@code outboxDir} as .eml and .html, for local development without a mail server
 * @param fromAddress the platform's sending address; the business name is the sender name (NTF-7)
 * @param linkBaseUrl where management links point (the booking site)
 * @param schedulerEnabled off in tests, which run the dispatcher directly
 */
@ConfigurationProperties("salon.notifications")
public record NotificationProperties(
        @DefaultValue("file") Delivery delivery,
        @DefaultValue("build/outbox") Path outboxDir,
        @DefaultValue("bookings@salonplatform.test") String fromAddress,
        @DefaultValue("http://localhost:8080") String linkBaseUrl,
        @DefaultValue("true") boolean schedulerEnabled,
        @DefaultValue("10s") Duration pollInterval,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("24h") Duration reminderBefore) {

    public enum Delivery { SMTP, FILE }
}
