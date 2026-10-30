package com.salonbooking.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void nothingIsBoundByDefault() {
        assertThat(TenantContext.currentBusinessId()).isEmpty();
        assertThat(TenantContext.isSystem()).isFalse();
        assertThatThrownBy(TenantContext::requireBusinessId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bindingEndsWithTheOperation() {
        UUID seen = TenantContext.callForBusiness(A, TenantContext::requireBusinessId);

        assertThat(seen).isEqualTo(A);
        assertThat(TenantContext.currentBusinessId()).isEmpty();
    }

    @Test
    void innerBindingWinsAndOuterIsRestored() {
        TenantContext.runForBusiness(A, () -> {
            assertThat(TenantContext.callForBusiness(B, TenantContext::currentBusinessId)).contains(B);
            assertThat(TenantContext.currentBusinessId()).contains(A);
        });
    }

    @Test
    void systemScopeHasNoBusinessUntilAJobBindsOne() {
        TenantContext.callAsSystem(() -> {
            assertThat(TenantContext.isSystem()).isTrue();
            assertThat(TenantContext.currentBusinessId()).isEmpty();

            Optional<UUID> perItem = TenantContext.callForBusiness(A, TenantContext::currentBusinessId);
            assertThat(perItem).contains(A);
            assertThat(TenantContext.callForBusiness(A, TenantContext::isSystem)).isFalse();
            return null;
        });
    }
}
