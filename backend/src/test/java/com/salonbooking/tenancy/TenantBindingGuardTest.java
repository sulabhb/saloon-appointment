package com.salonbooking.tenancy;

import static com.salonbooking.support.TestData.BUSINESS_A;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.salonbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Binding a business inside a transaction would not reach its connection, so it must fail loudly. */
class TenantBindingGuardTest extends IntegrationTest {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void bindingInsideATransactionIsRefused() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                TenantContext.runForBusiness(BUSINESS_A, () -> {})))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before a transaction starts");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                TenantContext.callAsSystem(() -> null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
