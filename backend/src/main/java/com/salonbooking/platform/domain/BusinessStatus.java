package com.salonbooking.platform.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Business lifecycle (TEN-4): Onboarding → Live ⇄ Suspended, and any → Closed. Closed is final. */
public enum BusinessStatus {
    ONBOARDING,
    LIVE,
    SUSPENDED,
    CLOSED;

    public boolean canChangeTo(BusinessStatus target) {
        return switch (this) {
            case ONBOARDING -> target == LIVE || target == CLOSED;
            case LIVE -> target == SUSPENDED || target == CLOSED;
            case SUSPENDED -> target == LIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }

    /** Stores the status in lower case, matching the database check constraint. */
    @Converter(autoApply = true)
    static class DbConverter implements AttributeConverter<BusinessStatus, String> {

        @Override
        public String convertToDatabaseColumn(BusinessStatus status) {
            return status == null ? null : status.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public BusinessStatus convertToEntityAttribute(String value) {
            return value == null ? null : BusinessStatus.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
