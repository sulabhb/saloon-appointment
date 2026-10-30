package com.salonbooking.identity.app;

import com.salonbooking.identity.data.AppUserRepository;
import com.salonbooking.identity.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first platform admin at start-up from {@code salon.bootstrap.platform-admin.*}
 * (for example the SALON_BOOTSTRAP_PLATFORM_ADMIN_EMAIL and _PASSWORD environment variables).
 *
 * <p>Does nothing when no email is configured. An existing account with that email is made a
 * platform admin and keeps its password. The password is never logged.
 */
@Component
@EnableConfigurationProperties(PlatformAdminBootstrap.Properties.class)
class PlatformAdminBootstrap implements ApplicationRunner {

    @ConfigurationProperties("salon.bootstrap.platform-admin")
    record Properties(String email, String password, @DefaultValue("Platform Admin") String fullName) {}

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final Properties properties;
    private final AppUserRepository users;
    private final AuthService authService;

    PlatformAdminBootstrap(Properties properties, AppUserRepository users, AuthService authService) {
        this.properties = properties;
        this.users = users;
        this.authService = authService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.email();
        if (email == null || email.isBlank()) {
            return;
        }
        users.findByEmail(email.trim()).ifPresentOrElse(existing -> {
            if (!existing.isPlatformAdmin()) {
                existing.grantPlatformAdmin();
                log.info("Granted platform admin to an existing account");
            }
        }, () -> {
            String password = properties.password();
            if (password == null || password.length() < InviteService.MIN_PASSWORD_LENGTH) {
                throw new IllegalStateException("salon.bootstrap.platform-admin.password must be at least "
                        + InviteService.MIN_PASSWORD_LENGTH + " characters");
            }
            AppUser admin = new AppUser(email.trim(), properties.fullName(), authService.hashPassword(password));
            admin.grantPlatformAdmin();
            users.save(admin);
            log.info("Created the bootstrap platform admin account");
        });
    }
}
