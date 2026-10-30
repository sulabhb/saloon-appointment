package com.salonbooking.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** A person's role within one business (requirements 4.1). */
public enum StaffRole {
    STYLIST,
    RECEPTION,
    MANAGER,
    OWNER;

    /** Owners see every branch; other roles only the branches they are assigned to. */
    public boolean seesAllBranches() {
        return this == OWNER;
    }

    public static StaffRole fromDb(String value) {
        return StaffRole.valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** Stores the role in lower case, matching the database check constraint. */
    @Converter(autoApply = true)
    static class DbConverter implements AttributeConverter<StaffRole, String> {

        @Override
        public String convertToDatabaseColumn(StaffRole role) {
            return role == null ? null : role.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public StaffRole convertToEntityAttribute(String value) {
            return value == null ? null : fromDb(value);
        }
    }
}
