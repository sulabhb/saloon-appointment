package com.salonbooking.notification.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the outbox in the background. Off in tests, which call the dispatcher directly. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "salon.notifications.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class NotificationScheduler {

    private final NotificationDispatcher dispatcher;

    NotificationScheduler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${salon.notifications.poll-interval:10s}", initialDelayString = "5s")
    void dispatch() {
        dispatcher.dispatchDue();
    }

    @Scheduled(fixedDelayString = "5m", initialDelayString = "30s")
    void reminders() {
        dispatcher.queueReminders();
    }
}
