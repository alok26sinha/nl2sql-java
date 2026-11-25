package io.nl2sql.core;

import io.nl2sql.core.config.DatabaseSettings;
import io.nl2sql.core.config.SshSettings;
import io.nl2sql.core.db.SshTunnel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SshTunnelSettingsTest {

    private static DatabaseSettings db(String type, String host, Integer port, String name) {
        DatabaseSettings d = new DatabaseSettings();
        d.setType(type);
        d.setHost(host);
        d.setPort(port);
        d.setName(name);
        d.setOptions("sslmode=require");
        d.getSsh().setHost("bastion");
        d.getSsh().setUsername("tunnel");
        return d;
    }

    @Test
    void offByDefault() {
        assertThat(new DatabaseSettings().getSsh().isEnabled()).isFalse();
        assertThat(new SshSettings().toString()).isEqualTo("off");
    }

    @Test
    void jdbcUrlPointsAtLocalEndpointAndTargetIsTheDatabase() {
        DatabaseSettings d = db("postgres", "10.0.3.17", null, "erp");
        assertThat(d.tunnelTargetHost()).isEqualTo("10.0.3.17");
        assertThat(d.tunnelTargetPort()).isEqualTo(5432);
        assertThat(d.jdbcUrlVia("127.0.0.1", 40123)).isEqualTo("jdbc:postgresql://127.0.0.1:40123/erp?sslmode=require");
        assertThat(d.jdbcUrl()).isEqualTo("jdbc:postgresql://10.0.3.17:5432/erp?sslmode=require");
    }

    @Test
    void explicitPortAndOtherDialects() {
        assertThat(db("mysql", "localhost", 3307, "tms").tunnelTargetPort()).isEqualTo(3307);
        assertThat(db("oracle", "db", null, "ORCLPDB1").jdbcUrlVia("127.0.0.1", 5000))
                .startsWith("jdbc:oracle:thin:@//127.0.0.1:5000/ORCLPDB1");
        DatabaseSettings sql = db("sqlserver", "srv01\\SQLEXPRESS", 49172, "sales");
        sql.setOptions(null);
        assertThat(sql.tunnelTargetHost()).isEqualTo("srv01");
        assertThat(sql.jdbcUrlVia("127.0.0.1", 5001)).isEqualTo("jdbc:sqlserver://127.0.0.1:5001;databaseName=sales");
    }

    @Test
    void refusesWhatCannotBeTunnelled() {
        DatabaseSettings url = db("postgres", "h", null, "erp");
        url.setUrl("jdbc:postgresql://h/erp");
        assertThatThrownBy(url::tunnelTargetHost).hasMessageContaining("not a full JDBC URL");
        assertThatThrownBy(() -> db("sqlite", null, null, "/a.db").tunnelTargetHost()).hasMessageContaining("SQLite");
        assertThatThrownBy(() -> db("sqlserver", "srv\\INST", null, "x").tunnelTargetPort()).hasMessageContaining("named instance");
    }

    @Test
    void pythonStyleUrlStillTunnels() {
        DatabaseSettings d = new DatabaseSettings();
        d.setUrl("mysql+pymysql://ro:pw@localhost:3306/shop");
        d.getSsh().setHost("bastion");
        assertThat(d.jdbcUrlVia("127.0.0.1", 6000)).isEqualTo("jdbc:mysql://127.0.0.1:6000/shop");
        assertThat(d.tunnelTargetHost()).isEqualTo("localhost");
    }

    @Test
    void tunnelValidatesCredentialsKeysAndKnownHosts(@TempDir Path dir) throws Exception {
        SshSettings s = new SshSettings();
        s.setHost("bastion");
        assertThatThrownBy(() -> SshTunnel.create(s, "db", 5432)).hasMessageContaining("user name");
        s.setUsername("tunnel");
        assertThatThrownBy(() -> SshTunnel.create(s, "db", 5432)).hasMessageContaining("private key file");
        s.setPrivateKey(dir.resolve("missing").toString());
        assertThatThrownBy(() -> SshTunnel.create(s, "db", 5432)).hasMessageContaining("cannot read the private key");
        s.setPrivateKey(null);
        s.setPassword("pw");
        s.setKnownHosts(dir.resolve("known_hosts").toString());
        assertThatThrownBy(() -> SshTunnel.create(s, "db", 5432)).hasMessageContaining("known_hosts");

        Files.writeString(dir.resolve("known_hosts"), "");
        try (SshTunnel t = SshTunnel.create(s, "db", 5432)) { // validates only; connects on first use
            assertThat(t.localPort()).isPositive();
        }
        s.setStrictHostKeyChecking(false);
        s.setLocalPort(45678);
        try (SshTunnel t = SshTunnel.create(s, "db", 5432)) {
            assertThat(t.localPort()).isEqualTo(45678);
        }
    }

    @Test
    void unreachableBastionFailsAsSqlExceptionNotAtStartup() {
        SshSettings s = new SshSettings();
        s.setHost("127.0.0.1");
        s.setPort(1); // nothing listens here
        s.setUsername("tunnel");
        s.setPassword("pw");
        s.setStrictHostKeyChecking(false);
        s.setConnectTimeoutSeconds(2);
        try (SshTunnel t = SshTunnel.create(s, "db", 5432)) {
            assertThatThrownBy(t::ensureOpen).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("SSH tunnel");
        }
    }

    @Test
    void secretsNotPrinted() {
        DatabaseSettings d = db("mysql", "h", null, "d");
        d.getSsh().setPassword("ssh-s3cret");
        d.getSsh().setPrivateKeyPassphrase("phr4se");
        assertThat(d.toString()).contains("ssh=tunnel@bastion:22").doesNotContain("ssh-s3cret").doesNotContain("phr4se");
    }
}
