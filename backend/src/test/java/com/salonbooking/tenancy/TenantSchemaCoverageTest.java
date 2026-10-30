package com.salonbooking.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.salonbooking.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Guards against new tables or entities that forget the tenant rules. */
class TenantSchemaCoverageTest extends IntegrationTest {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Set<String> tablesWithBusinessId() {
        return Set.copyOf(admin.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'public' AND column_name = 'business_id'""", String.class));
    }

    @Test
    void everyTableWithBusinessIdHasForcedRowLevelSecurity() {
        List<String> unprotected = admin.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN information_schema.columns col
                  ON col.table_schema = n.nspname AND col.table_name = c.relname AND col.column_name = 'business_id'
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                  AND (NOT c.relrowsecurity
                       OR NOT c.relforcerowsecurity
                       OR NOT EXISTS (SELECT 1 FROM pg_policies p
                                      WHERE p.schemaname = 'public' AND p.tablename = c.relname
                                        AND p.policyname = 'tenant_isolation'))""", String.class);

        assertThat(tablesWithBusinessId()).isNotEmpty();
        assertThat(unprotected).as("tables with business_id but without the tenant policy").isEmpty();
    }

    @Test
    void entitiesOnBusinessOwnedTablesExtendTenantOwnedEntity() {
        Set<String> businessOwnedTables = tablesWithBusinessId();

        Set<String> mismatched = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType())
                .filter(type -> businessOwnedTables.contains(type.getAnnotation(Table.class).name())
                        != TenantOwnedEntity.class.isAssignableFrom(type))
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(mismatched).as("entities whose tenant base class does not match their table").isEmpty();
    }
}
