package com.salonbooking.tenancy;

import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TenancyConfiguration {

    /** Matches no business; used when nothing is bound so tenant-owned queries return nothing. */
    static final UUID NO_TENANT = new UUID(0, 0);

    /** Wraps the application's pool. Flyway uses its own owner connection and is not wrapped. */
    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("dataSource".equals(beanName) && bean instanceof DataSource dataSource
                        && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }

    /** Feeds {@link TenantContext} to Hibernate's {@code @TenantId} filter. */
    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        CurrentTenantIdentifierResolver<UUID> resolver = new CurrentTenantIdentifierResolver<>() {
            @Override
            public UUID resolveCurrentTenantIdentifier() {
                return TenantContext.currentBusinessId().orElse(NO_TENANT);
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return false;
            }

            @Override
            public boolean isRoot(UUID tenantId) {
                return TenantContext.isSystem() && TenantContext.currentBusinessId().isEmpty();
            }
        };
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
