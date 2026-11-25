package io.nl2sql.core.db;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.nl2sql.core.ConfigurationException;
import io.nl2sql.core.config.SshSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Local port forwarding over SSH: {@code 127.0.0.1:localPort -> sshHost -> remoteHost:remotePort}.
 *
 * <p>The local port is fixed when the tunnel is created, so the JDBC URL never changes. The SSH session itself
 * connects lazily on {@link #ensureOpen()} and reconnects there if it dropped, so the service starts while the
 * bastion is unreachable, and recovers after a network blip.
 */
public final class SshTunnel implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SshTunnel.class);
    public static final String LOCAL_HOST = "127.0.0.1";

    private final SshSettings ssh;
    private final JSch jsch;
    private final String remoteHost;
    private final int remotePort;
    private final int localPort;
    private Session session;
    private boolean closed;

    private SshTunnel(SshSettings ssh, JSch jsch, String remoteHost, int remotePort, int localPort) {
        this.ssh = ssh;
        this.jsch = jsch;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.localPort = localPort;
    }

    /** Validates the settings and loads keys; does not connect yet. */
    public static SshTunnel create(SshSettings ssh, String remoteHost, int remotePort) {
        if (!ssh.isEnabled()) throw new ConfigurationException("SSH tunnel not configured (nl2sql.db.ssh.host)");
        if (ssh.getUsername() == null) {
            throw new ConfigurationException("SSH tunnel: the SSH user name is required (nl2sql.db.ssh.username / NL2SQL_DB_SSH_USERNAME)");
        }
        boolean hasPassword = ssh.getPassword() != null && !ssh.getPassword().isEmpty();
        if (ssh.getPrivateKey() == null && !hasPassword) {
            throw new ConfigurationException("SSH tunnel: set a private key file (nl2sql.db.ssh.private-key) "
                    + "or a password (nl2sql.db.ssh.password)");
        }
        JSch jsch = new JSch();
        try {
            if (ssh.getPrivateKey() != null) {
                Path key = expandHome(ssh.getPrivateKey());
                if (!Files.isReadable(key)) {
                    throw new ConfigurationException("SSH tunnel: cannot read the private key file " + key);
                }
                String phrase = ssh.getPrivateKeyPassphrase();
                jsch.addIdentity(key.toString(), phrase == null || phrase.isEmpty() ? null : phrase.getBytes(StandardCharsets.UTF_8));
            }
            if (ssh.isStrictHostKeyChecking()) {
                Path known = expandHome(ssh.getKnownHosts() != null ? ssh.getKnownHosts() : "~/.ssh/known_hosts");
                if (!Files.isReadable(known)) {
                    throw new ConfigurationException("SSH tunnel: host key checking is on but the known_hosts file " + known
                            + " cannot be read. Connect once with 'ssh " + ssh.getUsername() + "@" + ssh.getHost()
                            + "' (or run 'ssh-keyscan -p " + ssh.getPort() + " " + ssh.getHost() + " >> " + known
                            + "' and verify the fingerprint), or point nl2sql.db.ssh.known-hosts at the right file.");
                }
                jsch.setKnownHosts(known.toString());
            } else {
                log.warn("nl2sql: SSH host key checking is OFF for {} - the connection can be intercepted. "
                        + "Use it only for testing.", ssh.getHost());
            }
        } catch (JSchException e) {
            throw new ConfigurationException("SSH tunnel: " + e.getMessage());
        }
        int local = ssh.getLocalPort() > 0 ? ssh.getLocalPort() : freeLocalPort();
        return new SshTunnel(ssh, jsch, remoteHost, remotePort, local);
    }

    public int localPort() {
        return localPort;
    }

    /** Connects (or reconnects) the SSH session and the port forward if needed. */
    public synchronized void ensureOpen() throws SQLException {
        if (closed) throw new SQLException("SSH tunnel is closed");
        if (session != null && session.isConnected()) return;
        disconnect();
        try {
            Session s = jsch.getSession(ssh.getUsername(), ssh.getHost(), ssh.getPort());
            if (ssh.getPassword() != null && !ssh.getPassword().isEmpty()) s.setPassword(ssh.getPassword());
            s.setConfig("StrictHostKeyChecking", ssh.isStrictHostKeyChecking() ? "yes" : "no");
            if (ssh.getKeepAliveSeconds() > 0) {
                s.setServerAliveInterval(ssh.getKeepAliveSeconds() * 1000);
                s.setServerAliveCountMax(3);
            }
            s.connect(Math.max(1, ssh.getConnectTimeoutSeconds()) * 1000);
            try {
                s.setPortForwardingL(LOCAL_HOST, localPort, remoteHost, remotePort);
            } catch (JSchException e) {
                s.disconnect();
                throw e;
            }
            session = s;
            log.info("nl2sql: SSH tunnel {}:{} -> {}@{}:{} -> {}:{} is up", LOCAL_HOST, localPort,
                    ssh.getUsername(), ssh.getHost(), ssh.getPort(), remoteHost, remotePort);
        } catch (JSchException e) {
            throw new SQLException("SSH tunnel to " + ssh.getUsername() + "@" + ssh.getHost() + ":" + ssh.getPort()
                    + " failed: " + e.getMessage(), "08001", e);
        }
    }

    private void disconnect() {
        if (session == null) return;
        try {
            session.delPortForwardingL(LOCAL_HOST, localPort);
        } catch (JSchException ignored) {
            // the session is already gone
        }
        session.disconnect();
        session = null;
    }

    @Override
    public synchronized void close() {
        closed = true;
        disconnect();
    }

    private static int freeLocalPort() {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new ConfigurationException("SSH tunnel: no free local port: " + e.getMessage());
        }
    }

    static Path expandHome(String path) {
        String p = path.trim();
        if (p.equals("~") || p.startsWith("~/") || p.startsWith("~\\")) {
            p = System.getProperty("user.home") + p.substring(1);
        }
        return Path.of(p);
    }
}
