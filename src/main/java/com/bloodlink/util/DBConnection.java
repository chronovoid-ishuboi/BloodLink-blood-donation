package com.bloodlink.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * All BloodLink data lives wherever {@code db.url} points. There is
 * intentionally no automatic embedded-database fallback: if the configured
 * database is unreachable, every call here fails loudly rather than silently
 * switching to a different, differently-populated database. (Explicitly
 * setting {@code db.url} to a {@code jdbc:h2:} URL for offline development is
 * a separate, supported path that both this class and {@link DatabaseSetup}
 * detect and adapt to.)
 *
 * <h2>Connection pooling</h2>
 * Connections are pooled and reused. This is not a micro-optimisation: the
 * deployed database is a TLS cloud instance, and opening a connection to it
 * measures at roughly 450ms from a developer machine. Every DAO method here
 * opens and closes one, so a single dashboard refresh -- matches,
 * notifications, donations, reputation, hospitals, points, vouchers -- was
 * paying that cost eight or nine times over, which is most of why the app
 * felt slow, and why signing in took seconds.
 * <p>
 * Callers are unchanged: {@link #getConnection()} hands back a proxy whose
 * {@code close()} returns it to the pool instead of closing it, so the
 * existing try-with-resources in every DAO keeps working and keeps being
 * correct. A connection that has gone stale is validated and discarded on
 * borrow rather than handed out to fail mid-query.
 */
public final class DBConnection {

    /** Enough for the UI thread, the background refresh, and the push listener without queueing. */
    private static final int MAX_IDLE = 6;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;
    /** A connection returned more recently than this is handed straight back out without a validation round trip. */
    private static final long TRUST_WINDOW_MILLIS = 30_000;

    private static volatile boolean driverLoaded = false;
    private static volatile boolean schemaInitialized = false;

    /** A pooled connection plus when it went back in, so borrow can decide whether it still needs checking. */
    private record Idle(Connection connection, long returnedAt) { }

    private static final Deque<Idle> idle = new ArrayDeque<>();
    private static final Object POOL_LOCK = new Object();
    private static volatile boolean shutdown = false;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DBConnection::closePool, "bloodlink-db-shutdown"));
    }

    private DBConnection() { }

    public static Connection getConnection() throws SQLException {
        ensureDriverLoaded();
        // Schema initialisation itself needs connections, so the guard must be
        // set before calling it or this recurses.
        if (!schemaInitialized) {
            synchronized (DBConnection.class) {
                if (!schemaInitialized) {
                    schemaInitialized = true;
                    DatabaseSetup.ensureInitialized();
                }
            }
        }
        return borrow();
    }

    /** Like {@link #getConnection()} but never triggers schema initialization -- used by setup code itself. */
    public static Connection getRawConnection() throws SQLException {
        ensureDriverLoaded();
        return borrow();
    }

    public static String getActiveUrl() {
        return AppConfig.get("db.url");
    }

    private static Connection borrow() throws SQLException {
        while (true) {
            Idle pooled;
            synchronized (POOL_LOCK) {
                pooled = idle.pollFirst();
            }
            if (pooled == null) break;
            try {
                // isValid() is itself a network round trip -- about 100ms to a
                // cloud instance -- so validating on every single borrow gives
                // back much of what pooling just saved. A connection returned
                // moments ago is trusted; only one that has sat idle long
                // enough to have been dropped by the server is checked.
                boolean fresh = System.currentTimeMillis() - pooled.returnedAt() < TRUST_WINDOW_MILLIS;
                if (fresh || pooled.connection().isValid(VALIDATION_TIMEOUT_SECONDS)) {
                    return wrap(pooled.connection());
                }
            } catch (SQLException ignored) {
                // Treated the same as invalid: discarded below.
            }
            closeQuietly(pooled.connection());
        }
        return wrap(openConnection());
    }

    private static void release(Connection real) {
        if (shutdown) {
            closeQuietly(real);
            return;
        }
        try {
            // A connection handed back mid-transaction would poison whoever
            // borrows it next, so anything uncommitted is rolled back and
            // autocommit restored before it goes back in the pool.
            if (!real.getAutoCommit()) {
                real.rollback();
                real.setAutoCommit(true);
            }
            if (real.isClosed()) return;
        } catch (SQLException e) {
            closeQuietly(real);
            return;
        }
        synchronized (POOL_LOCK) {
            if (idle.size() < MAX_IDLE) {
                idle.addLast(new Idle(real, System.currentTimeMillis()));
                return;
            }
        }
        closeQuietly(real);
    }

    /**
     * Wraps a real connection so {@code close()} returns it to the pool. Every
     * other call passes straight through.
     */
    private static Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                DBConnection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new PooledHandler(real));
    }

    private static final class PooledHandler implements InvocationHandler {
        private final Connection real;
        private boolean returned = false;

        PooledHandler(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "close" -> {
                    // Idempotent: a double close must not put the same
                    // connection into the pool twice.
                    if (!returned) {
                        returned = true;
                        release(real);
                    }
                    return null;
                }
                case "isClosed" -> {
                    return returned || real.isClosed();
                }
                default -> {
                    if (returned) {
                        throw new SQLException("This connection has already been returned to the pool.");
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            }
        }
    }

    private static Connection openConnection() throws SQLException {
        String url = AppConfig.get("db.url");
        try {
            return DriverManager.getConnection(url, AppConfig.get("db.username"), AppConfig.get("db.password"));
        } catch (SQLException e) {
            throw new SQLException("Could not connect to the BloodLink database at " + url + ": " + e.getMessage(), e);
        }
    }

    private static void ensureDriverLoaded() throws SQLException {
        if (driverLoaded) return;
        try {
            String url = getActiveUrl();
            if (url != null && url.startsWith("jdbc:h2:")) {
                Class.forName("org.h2.Driver");
            } else {
                Class.forName("com.mysql.cj.jdbc.Driver");
            }
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver not found on classpath.", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while discarding a connection.
        }
    }

    private static void closePool() {
        shutdown = true;
        synchronized (POOL_LOCK) {
            Idle pooled;
            while ((pooled = idle.pollFirst()) != null) {
                closeQuietly(pooled.connection());
            }
        }
    }

    public static boolean testConnection() {
        try (Connection connection = getRawConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
