package io.nl2sql.core.config;

/**
 * Optional SSH tunnel (TCP/IP over SSH) to reach a database that is only reachable from a bastion / jump host.
 * The tunnel is used when {@link #host} is set: nl2sql connects to the SSH server, forwards a local loopback port
 * to {@code nl2sql.db.host:nl2sql.db.port} <em>as seen from the SSH server</em>, and points JDBC at that port.
 *
 * <p>Authenticate with a private key ({@link #privateKey}, optionally with {@link #privateKeyPassphrase}) or a
 * {@link #password}. Host keys are checked against {@link #knownHosts} by default.
 */
public class SshSettings {
    /** SSH server (bastion / jump host). Setting it turns the tunnel on. */
    private String host;
    private int port = 22;
    private String username;
    /** Password authentication (prefer a key). */
    private String password;
    /** Path to a private key file (OpenSSH, PEM or PuTTY .ppk format). */
    private String privateKey;
    private String privateKeyPassphrase;
    /** known_hosts file to verify the server key against; default {@code ~/.ssh/known_hosts}. */
    private String knownHosts;
    /** Reject unknown or changed host keys. Turn off only for testing. */
    private boolean strictHostKeyChecking = true;
    /** Local loopback port for the tunnel; 0 picks a free one. */
    private int localPort = 0;
    private int connectTimeoutSeconds = 15;
    /** Keep-alive interval; 0 disables keep-alives. */
    private int keepAliveSeconds = 30;

    /** True when a tunnel is configured. */
    public boolean isEnabled() {
        return DatabaseSettings.notBlank(host);
    }

    public String getHost() { return DatabaseSettings.notBlank(host) ? host.trim() : null; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getUsername() { return DatabaseSettings.notBlank(username) ? username.trim() : null; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPrivateKey() { return DatabaseSettings.notBlank(privateKey) ? privateKey.trim() : null; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getPrivateKeyPassphrase() { return privateKeyPassphrase; }
    public void setPrivateKeyPassphrase(String privateKeyPassphrase) { this.privateKeyPassphrase = privateKeyPassphrase; }
    public String getKnownHosts() { return DatabaseSettings.notBlank(knownHosts) ? knownHosts.trim() : null; }
    public void setKnownHosts(String knownHosts) { this.knownHosts = knownHosts; }
    public boolean isStrictHostKeyChecking() { return strictHostKeyChecking; }
    public void setStrictHostKeyChecking(boolean strictHostKeyChecking) { this.strictHostKeyChecking = strictHostKeyChecking; }
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) { this.localPort = localPort; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }

    @Override
    public String toString() { // never print secrets
        if (!isEnabled()) return "off";
        return username + "@" + getHost() + ":" + port
                + (getPrivateKey() != null ? " key=" + getPrivateKey() : "")
                + (password != null && !password.isEmpty() ? " password=<set>" : "");
    }
}
