package com.salonbooking.booking.app;

import java.sql.SQLException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Runs one write inside a savepoint of the current transaction, so a constraint violation can be
 * undone and the transaction carry on (trying the next stylist, say).
 */
final class Savepoints {

    /** The stylist is busy then: the no-overlap exclusion constraint. */
    static final String OVERLAP = "23P01";
    /** Another business row already uses the random booking reference. */
    static final String REFERENCE_TAKEN = "reference";
    static final String OTHER_CONFLICT = "conflict";

    private Savepoints() {}

    /** Empty on success; otherwise what went wrong, with the savepoint rolled back. */
    static Optional<String> attempt(JdbcClient jdbc, Runnable write) {
        jdbc.sql("SAVEPOINT attempt").update();
        try {
            write.run();
            jdbc.sql("RELEASE SAVEPOINT attempt").update();
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            jdbc.sql("ROLLBACK TO SAVEPOINT attempt").update();
            String state = sqlState(e);
            if (OVERLAP.equals(state)) {
                return Optional.of(OVERLAP);
            }
            boolean reference = "23505".equals(state) && String.valueOf(e.getMostSpecificCause().getMessage()).contains("reference");
            return Optional.of(reference ? REFERENCE_TAKEN : OTHER_CONFLICT);
        }
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
