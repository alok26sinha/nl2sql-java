package io.nl2sql.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nl2sql.core.config.DatabaseSettings;
import io.nl2sql.core.config.Dialect;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Builds the connection pool. It connects lazily, so the service starts even while the database is down.
 * With {@code nl2sql.db.ssh.host} set, connections go through an SSH tunnel that opens on first use.
 */
public final class DataSourceFactory {
    private DataSourceFactory() {
    }

    public static HikariDataSource create(DatabaseSettings db, int queryTimeoutSeconds) {
        Dialect dialect = db.dialect();
        SshTunnel tunnel = null;
        String url;
        if (db.getSsh().isEnabled()) {
            tunnel = SshTunnel.create(db.getSsh(), db.tunnelTargetHost(), db.tunnelTargetPort());
            url = db.jdbcUrlVia(SshTunnel.LOCAL_HOST, tunnel.localPort());
        } else {
            url = db.jdbcUrl();
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("nl2sql");
        cfg.setJdbcUrl(url);
        if (db.getUsername() != null && !db.getUsername().isBlank()) cfg.setUsername(db.getUsername());
        if (db.getPassword() != null && !db.getPassword().isEmpty()) cfg.setPassword(db.getPassword());
        cfg.setMaximumPoolSize(Math.max(1, db.getPoolSize()));
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(15_000);
        cfg.setInitializationFailTimeout(-1);
        switch (dialect) {
            case MYSQL -> {
                cfg.addDataSourceProperty("connectTimeout", "15000");
                cfg.addDataSourceProperty("useCursorFetch", "false");
            }
            case MARIADB -> cfg.addDataSourceProperty("connectTimeout", "15000");
            case POSTGRES -> {
                cfg.addDataSourceProperty("connectTimeout", "15");
                cfg.addDataSourceProperty("ApplicationName", "nl2sql");
            }
            case SQLSERVER -> {
                cfg.addDataSourceProperty("loginTimeout", "15");
                cfg.addDataSourceProperty("applicationName", "nl2sql");
            }
            case ORACLE -> cfg.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "15000");
            case SQLITE -> {
                cfg.addDataSourceProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
                cfg.setReadOnly(true); // Hikari re-applies this flag; sqlite-jdbc refuses to change it once open
                cfg.setMaximumPoolSize(1);
            }
        }
        return tunnel == null ? new HikariDataSource(cfg) : new TunneledDataSource(cfg, tunnel);
    }

    /** A pool whose connections go through an SSH tunnel; the tunnel is (re)opened before each borrow. */
    static final class TunneledDataSource extends HikariDataSource {
        private final SshTunnel tunnel;

        TunneledDataSource(HikariConfig cfg, SshTunnel tunnel) {
            super(cfg);
            this.tunnel = tunnel;
        }

        @Override
        public Connection getConnection() throws SQLException {
            tunnel.ensureOpen();
            return super.getConnection();
        }

        @Override
        public void close() {
            try {
                super.close();
            } finally {
                if (tunnel != null) tunnel.close(); // null only if the pool constructor failed
            }
        }
    }
}
