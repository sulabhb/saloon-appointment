package com.salonbooking.notification.app;

import com.salonbooking.notification.mail.OutgoingEmail;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Customer emails (NTF-1..3, NTF-7, MGT-6). Plain inline-styled HTML that renders in every mail
 * app, with a text alternative. Pure: no Spring, no database.
 */
public final class EmailRenderer {

    public record Service(String name, int durationMin) {}

    /** Everything an email can mention. {@code manageUrl} is null for cancellations. */
    public record Data(NotificationType type, String businessName, String businessSlug, String accentColor,
            String branchName, String branchAddress, String branchPhone, String replyTo, ZoneId zone, Instant start,
            Instant end, List<Service> services, String staffName, String customerName, String reference,
            boolean cancelledBySalon, boolean canCancel, String manageUrl, String bookAgainUrl) {}

    private static final Locale AU = Locale.forLanguageTag("en-AU");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", AU);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM", AU);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", AU);
    private static final DateTimeFormatter ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String FALLBACK_BRAND = "#2F5D50";

    private EmailRenderer() {}

    public static OutgoingEmail render(Data d, String fromAddress, String to) {
        String day = DAY.format(d.start().atZone(d.zone()));
        String time = TIME.format(d.start().atZone(d.zone()));
        String when = "%s at %s".formatted(day, time);
        String first = d.customerName().trim().split("\\s+")[0];

        String subject;
        String headline;
        String intro;
        String button = null;
        String buttonUrl = null;
        boolean calendar = false;
        switch (d.type()) {
            case BOOKING_CONFIRMED -> {
                subject = "Booking confirmed: %s at %s".formatted(SHORT_DAY.format(d.start().atZone(d.zone())), time);
                headline = "You're booked in";
                intro = "Hi %s, here are your booking details.".formatted(first);
                button = "View or cancel booking";
                buttonUrl = d.manageUrl();
                calendar = true;
            }
            case BOOKING_RESCHEDULED -> {
                subject = "Booking moved: %s at %s".formatted(SHORT_DAY.format(d.start().atZone(d.zone())), time);
                headline = "Your booking has a new time";
                intro = "Hi %s, your booking has moved. Here are the new details.".formatted(first);
                button = "View or cancel booking";
                buttonUrl = d.manageUrl();
                calendar = true;
            }
            case BOOKING_CANCELLED -> {
                subject = "Booking cancelled: %s at %s".formatted(SHORT_DAY.format(d.start().atZone(d.zone())), time);
                headline = "Your booking is cancelled";
                intro = d.cancelledBySalon()
                        ? "Hi %s, %s cancelled this booking. Reply to this email%s to arrange another time."
                                .formatted(first, d.businessName(), d.branchPhone() == null ? "" : " or call " + d.branchPhone())
                        : "Hi %s, your booking is cancelled as you asked.".formatted(first);
                button = "Book again";
                buttonUrl = d.bookAgainUrl();
            }
            case REMINDER -> {
                subject = "Reminder: %s at %s".formatted(SHORT_DAY.format(d.start().atZone(d.zone())), time);
                headline = "See you soon";
                intro = "Hi %s, this is a reminder of your booking at %s.".formatted(first, d.businessName());
                button = d.canCancel() ? "View or cancel booking" : "View booking";
                buttonUrl = d.manageUrl();
            }
            case MANAGE_LINK -> {
                subject = "Your booking link for %s".formatted(d.businessName());
                headline = "Here's your booking link";
                intro = "Hi %s, use this link to check or cancel your booking. Earlier links no longer work.".formatted(first);
                button = "View booking";
                buttonUrl = d.manageUrl();
            }
            default -> throw new IllegalArgumentException(d.type().name());
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"When", "%s, %s to %s".formatted(day, time, TIME.format(d.end().atZone(d.zone())))});
        rows.add(new String[] {"Services", String.join(", ", d.services().stream().map(Service::name).toList())});
        rows.add(new String[] {"With", d.staffName()});
        rows.add(new String[] {"Where", "%s, %s".formatted(d.branchName(), d.branchAddress())});
        if (d.branchPhone() != null) {
            rows.add(new String[] {"Phone", d.branchPhone()});
        }
        rows.add(new String[] {"Reference", d.reference()});

        String brand = d.accentColor() == null ? FALLBACK_BRAND : d.accentColor();
        String onBrand = readableTextOn(brand);
        String footer = "You're getting this email because you booked with %s. Reply to contact the salon."
                .formatted(d.businessName());

        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:#f3f2f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;color:#1c1b1a">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f2f0"><tr><td align="center" style="padding:24px 12px">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border-radius:14px;overflow:hidden">
                <tr><td style="background:%s;color:%s;padding:20px 28px;font-size:20px;font-weight:600">%s</td></tr>
                <tr><td style="padding:28px">
                <h1 style="margin:0 0 6px;font-size:22px;line-height:1.25">%s</h1>
                <p style="margin:0 0 20px;font-size:15px;line-height:1.5;color:#3b3835">%s</p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-top:1px solid #e6e4e1;font-size:15px;line-height:1.45">
                """.formatted(esc(subject), brand, onBrand, esc(d.businessName()), esc(headline), esc(intro)));
        for (String[] row : rows) {
            html.append("""
                    <tr><td style="padding:10px 12px 10px 0;border-bottom:1px solid #e6e4e1;color:#6e6a65;width:96px;vertical-align:top">%s</td>\
                    <td style="padding:10px 0;border-bottom:1px solid #e6e4e1">%s</td></tr>
                    """.formatted(esc(row[0]), esc(row[1])));
        }
        html.append("</table>");
        if (buttonUrl != null) {
            html.append("""
                    <p style="margin:24px 0 0"><a href="%s" style="display:inline-block;background:%s;color:%s;text-decoration:none;font-weight:600;padding:12px 22px;border-radius:999px">%s</a></p>
                    """.formatted(esc(buttonUrl), brand, onBrand, esc(button)));
            if (d.manageUrl() != null && buttonUrl.equals(d.manageUrl())) {
                html.append("""
                        <p style="margin:12px 0 0;font-size:13px;line-height:1.5;color:#6e6a65">This link is private: anyone with it can see or cancel this booking.</p>
                        """);
            }
        }
        html.append("""
                </td></tr></table>
                <p style="max-width:520px;margin:16px auto 0;font-size:12px;line-height:1.5;color:#8f8a84">%s</p>
                </td></tr></table></body></html>
                """.formatted(esc(footer)));

        StringBuilder text = new StringBuilder();
        text.append(d.businessName()).append("\n\n").append(headline).append("\n").append(intro).append("\n\n");
        for (String[] row : rows) {
            text.append(row[0]).append(": ").append(row[1]).append("\n");
        }
        if (buttonUrl != null) {
            text.append("\n").append(button).append(": ").append(buttonUrl).append("\n");
        }
        text.append("\n").append(footer).append("\n");

        List<OutgoingEmail.Attachment> attachments = calendar
                ? List.of(new OutgoingEmail.Attachment("booking-%s.ics".formatted(d.reference()), "text/calendar; charset=utf-8; method=PUBLISH",
                        ics(d).getBytes(StandardCharsets.UTF_8)))
                : List.of();
        return new OutgoingEmail(d.businessName(), fromAddress, d.replyTo(), to, subject, html.toString(), text.toString(),
                attachments);
    }

    /** A calendar entry for the appointment (NTF-2), in UTC so every calendar app places it right. */
    static String ics(Data d) {
        String services = String.join(", ", d.services().stream().map(Service::name).toList());
        return String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Salon Booking//EN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:%s@%s".formatted(d.reference(), d.businessSlug()),
                "DTSTAMP:" + ICS.format(Instant.now()),
                "DTSTART:" + ICS.format(d.start()),
                "DTEND:" + ICS.format(d.end()),
                "SUMMARY:" + icsText("%s at %s".formatted(services, d.businessName())),
                "LOCATION:" + icsText("%s, %s".formatted(d.branchName(), d.branchAddress())),
                "DESCRIPTION:" + icsText("With %s. Reference %s.%s".formatted(d.staffName(), d.reference(),
                        d.manageUrl() == null ? "" : " Change or cancel: " + d.manageUrl())),
                "END:VEVENT",
                "END:VCALENDAR",
                "");
    }

    /** White on the brand colour unless that fails WCAG AA (4.5:1); then near-black. Same rule as the web and Android. */
    static String readableTextOn(String hex) {
        int value = Integer.parseInt(hex.substring(1), 16);
        double luminance = 0.2126 * channel(value >> 16) + 0.7152 * channel(value >> 8) + 0.0722 * channel(value);
        return 1.05 / (luminance + 0.05) >= 4.5 ? "#ffffff" : "#1a1a1a";
    }

    private static double channel(int shifted) {
        double c = (shifted & 0xff) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static String icsText(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n");
    }

    private static String esc(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
