package io.trellis.platform.aspect.lock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Session-level {@code pg_try_advisory_lock} on a dedicated connection taken straight from the pool (not the
 * transaction's connection), held until the handle closes. Lock ids are the first 8 bytes of SHA-256 of the
 * name, the same scheme DataHub uses for its queue locks, so collisions are negligible.
 */
public final class PostgresAdvisoryLockProvider implements LockProvider {
    private static final String TRY_LOCK = "SELECT pg_try_advisory_lock(?)";
    private static final String UNLOCK = "SELECT pg_advisory_unlock(?)";

    private final DataSource dataSource;
    private final Duration pollInterval;
    private final Clock clock;

    public PostgresAdvisoryLockProvider(DataSource dataSource, Duration pollInterval, Clock clock) {
        this.dataSource = dataSource;
        this.pollInterval = pollInterval;
        this.clock = clock;
    }

    @Override
    public Optional<Handle> tryLock(String name, Duration waitFor) {
        long id = lockId(name);
        Instant deadline = clock.instant().plus(waitFor);
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            while (true) {
                if (query(connection, TRY_LOCK, id)) {
                    return Optional.of(handle(connection, id));
                }
                if (!clock.instant().isBefore(deadline)) {
                    connection.close();
                    return Optional.empty();
                }
                Thread.sleep(pollInterval);
            }
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new DataAccessResourceFailureException("Advisory lock failed for " + name, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeQuietly(connection);
            return Optional.empty();
        }
    }

    private static Handle handle(Connection connection, long id) {
        return new Handle() {
            private boolean released;

            @Override
            public void close() {
                if (released) {
                    return;
                }
                released = true;
                try {
                    query(connection, UNLOCK, id);
                } catch (SQLException e) {
                    // Closing the connection below ends the session, which drops the lock anyway.
                } finally {
                    closeQuietly(connection);
                }
            }
        };
    }

    private static boolean query(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    static long lockId(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do: the pool discards broken connections.
        }
    }
}
