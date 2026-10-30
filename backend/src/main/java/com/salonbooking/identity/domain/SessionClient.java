package com.salonbooking.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Where a session was signed in. Decides how the token travels and how long it lives. */
public enum SessionClient {
    /** Token in an HTTP-only cookie; CSRF protection applies. */
    WEB,
    /** Token returned once and sent as {@code Authorization: Bearer}. */
    ANDROID;

    public static SessionClient parse(String value) {
        return SessionClient.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Converter(autoApply = true)
    static class DbConverter implements AttributeConverter<SessionClient, String> {

        @Override
        public String convertToDatabaseColumn(SessionClient client) {
            return client == null ? null : client.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public SessionClient convertToEntityAttribute(String value) {
            return value == null ? null : parse(value);
        }
    }
}
