package com.salonbooking.booking.domain;

import java.util.Locale;
import java.util.Optional;

/** Normalising customer contact details so the same person matches exactly (D-5). */
public final class Contacts {

    private Contacts() {}

    public static String email(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * An Australian or international mobile as E.164 ("0412 345 678" and "+61 412 345 678" both
     * become "+61412345678"), or empty if it does not look like a phone number.
     */
    public static Optional<String> phone(String raw) {
        String digits = raw.replaceAll("[\\s().-]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        } else if (digits.startsWith("0")) {
            digits = "+61" + digits.substring(1);
        }
        return digits.matches("^\\+[1-9][0-9]{7,14}$") ? Optional.of(digits) : Optional.empty();
    }

    /** "j***@example.com" (MGT-2). */
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }

    /** "*** *** 678": only the last three digits (MGT-2). */
    public static String maskPhone(String phone) {
        return phone.length() <= 3 ? "***" : "*** *** " + phone.substring(phone.length() - 3);
    }
}
