package com.salonbooking.notification.mail;

import com.salonbooking.notification.app.NotificationProperties;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
class MailConfiguration {

    @Bean
    MailGateway mailGateway(NotificationProperties properties, ObjectProvider<JavaMailSender> smtp, Clock clock) {
        return switch (properties.delivery()) {
            case SMTP -> new SmtpMailGateway(smtp.getIfAvailable(() -> {
                throw new IllegalStateException("salon.notifications.delivery=smtp needs spring.mail.host");
            }));
            case FILE -> new FileMailGateway(properties.outboxDir(), clock);
        };
    }
}
