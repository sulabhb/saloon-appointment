package com.salonbooking.identity.app;

import com.salonbooking.notification.app.NotificationProperties;
import com.salonbooking.notification.mail.MailGateway;
import com.salonbooking.notification.mail.OutgoingEmail;
import com.salonbooking.shared.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Staff password reset by email (DSH-1). Requests get the same answer, and take about the same
 * time, whether or not the address has an account: the email is sent in the background after the
 * request commits.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final Duration LINK_LIFETIME = Duration.ofHours(1);

    private record Account(UUID id, String email, String fullName) {}

    private final JdbcClient jdbc;
    private final AuthService auth;
    private final MailGateway mail;
    private final NotificationProperties notifications;
    private final AuthProperties properties;
    private final Clock clock;

    PasswordResetService(JdbcClient jdbc, AuthService auth, MailGateway mail, NotificationProperties notifications,
            AuthProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.mail = mail;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    /** Emails a reset link to an active account; earlier unused links stop working. */
    @Transactional
    public void request(String email) {
        Optional<Account> account = jdbc.sql("SELECT id, email, full_name FROM app_user WHERE email = :email AND active")
                .param("email", email.trim().toLowerCase(Locale.ROOT))
                .query((rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("full_name")))
                .optional();
        if (account.isEmpty()) {
            return;
        }
        Account a = account.get();
        jdbc.sql("UPDATE password_reset SET used_at = now() WHERE user_id = :userId AND used_at IS NULL")
                .param("userId", a.id()).update();
        String token = SessionTokens.newToken();
        jdbc.sql("""
                        INSERT INTO password_reset (id, user_id, token_hash, expires_at)
                        VALUES (:id, :userId, :hash, :expiresAt)""")
                .param("id", UUID.randomUUID()).param("userId", a.id()).param("hash", SessionTokens.hash(token))
                .param("expiresAt", Timestamp.from(clock.instant().plus(LINK_LIFETIME)))
                .update();
        String link = "%s/reset-password/%s".formatted(properties.appBaseUrl(), token);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Thread.ofVirtual().name("password-reset-mail").start(() -> send(a, link));
            }
        });
    }

    /** Sets a new password from a valid link, then signs the person out everywhere. */
    @Transactional
    public void reset(String token, String newPassword) {
        if (newPassword.length() < InviteService.MIN_PASSWORD_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Choose a password of at least " + InviteService.MIN_PASSWORD_LENGTH + " characters.");
        }
        UUID userId = jdbc.sql("""
                        UPDATE password_reset SET used_at = now()
                        WHERE token_hash = :hash AND used_at IS NULL AND expires_at > now()
                        RETURNING user_id""")
                .param("hash", SessionTokens.hash(token))
                .query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "LINK_EXPIRED",
                        "This link has expired or was already used. Ask for a new one."));
        jdbc.sql("""
                        UPDATE app_user SET password_hash = :hash, failed_logins = 0, locked_until = NULL, updated_at = now()
                        WHERE id = :id""")
                .param("hash", auth.hashPassword(newPassword)).param("id", userId)
                .update();
        jdbc.sql("UPDATE auth_session SET revoked_at = now(), updated_at = now() WHERE user_id = :id AND revoked_at IS NULL")
                .param("id", userId).update();
    }

    private void send(Account a, String link) {
        String first = a.fullName().trim().split("\\s+")[0];
        String text = """
                Hi %s,

                Someone asked to reset the password for your salon staff account (%s). To choose a new password, open this link within an hour:

                %s

                If it wasn't you, ignore this email; your password stays the same.
                """.formatted(first, a.email(), link);
        String html = """
                <!doctype html><html lang="en"><body style="margin:0;padding:24px 12px;background:#f3f2f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif;color:#1c1b1a">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:14px">
                <tr><td style="padding:28px">
                <h1 style="margin:0 0 8px;font-size:20px">Reset your password</h1>
                <p style="margin:0 0 20px;font-size:15px;line-height:1.5;color:#3b3835">Hi %s, someone asked to reset the password for your salon staff account (%s). The link works once, for an hour.</p>
                <p style="margin:0"><a href="%s" style="display:inline-block;background:#3f5a4a;color:#ffffff;text-decoration:none;font-weight:600;padding:12px 22px;border-radius:999px">Choose a new password</a></p>
                <p style="margin:20px 0 0;font-size:13px;line-height:1.5;color:#6e6a65">If it wasn't you, ignore this email; your password stays the same.</p>
                </td></tr></table></body></html>
                """.formatted(esc(first), esc(a.email()), esc(link));
        try {
            mail.send(new OutgoingEmail("Salon Booking", notifications.fromAddress(), null, a.email(),
                    "Reset your password", html, text, List.of()));
        } catch (Exception e) {
            log.warn("Password reset email to user {} failed: {}", a.id(), e.getMessage());
        }
    }

    private static String esc(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
