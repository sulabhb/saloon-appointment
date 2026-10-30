package com.salonbooking.notification.app;

import java.util.Locale;

/** The customer emails the platform sends (NTF-1, NTF-3, MGT-6). */
public enum NotificationType {
    BOOKING_CONFIRMED,
    BOOKING_RESCHEDULED,
    BOOKING_CANCELLED,
    REMINDER,
    MANAGE_LINK;

    public String db() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NotificationType fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
