package com.salonbooking.tenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The business (tenant) the current unit of work runs for.
 *
 * <p>Bound only by trusted code: request filters (from the URL slug, a management token or the
 * staff session) and background jobs (from the row being processed). Nothing else should read the
 * business from request parameters.
 *
 * <p>System scope is for the few reviewed paths that must see rows of all businesses, such as
 * claiming due jobs. Search for {@code callAsSystem} to find them.
 *
 * <p>Bind before a transaction starts, never inside one: the database connection takes its
 * business when it is checked out, so a binding made mid-transaction would not reach it. The bind
 * methods refuse to run inside an active transaction for that reason.
 */
public final class TenantContext {

    private record Scope(UUID businessId, boolean system) {}

    private static final ScopedValue<Scope> SCOPE = ScopedValue.newInstance();

    private TenantContext() {}

    public static Optional<UUID> currentBusinessId() {
        return SCOPE.isBound() ? Optional.ofNullable(SCOPE.get().businessId()) : Optional.empty();
    }

    public static UUID requireBusinessId() {
        return currentBusinessId().orElseThrow(() -> new IllegalStateException("No business is bound to this unit of work"));
    }

    public static boolean isSystem() {
        return SCOPE.isBound() && SCOPE.get().system();
    }

    public static <T> T callForBusiness(UUID businessId, Supplier<T> operation) {
        return bind(new Scope(Objects.requireNonNull(businessId), false)).call(operation::get);
    }

    public static void runForBusiness(UUID businessId, Runnable operation) {
        bind(new Scope(Objects.requireNonNull(businessId), false)).run(operation);
    }

    /** Variant for callers that throw checked exceptions, such as servlet filters. */
    public static <T, X extends Throwable> T callForBusinessChecked(UUID businessId, ScopedValue.CallableOp<T, X> operation) throws X {
        return bind(new Scope(Objects.requireNonNull(businessId), false)).call(operation);
    }

    public static <T> T callAsSystem(Supplier<T> operation) {
        return bind(new Scope(null, true)).call(operation::get);
    }

    private static ScopedValue.Carrier bind(Scope scope) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Tenant scope must be bound before a transaction starts; the open transaction's connection would keep its old business");
        }
        return ScopedValue.where(SCOPE, scope);
    }
}
