package io.nl2sql.core.schema;

import com.zaxxer.hikari.HikariDataSource;
import io.nl2sql.core.TestDatabase;
import io.nl2sql.core.config.DatabaseSettings;
import io.nl2sql.core.config.Dialect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fewer round trips when reading the schema (each one costs a network delay through an SSH bastion):
 * MySQL/MariaDB read from information_schema, and one sample query per table.
 */
class FastSchemaTest {

    @Test
    void mysqlTypesLookLikeJdbcMetadata() {
        assertThat(SchemaIntrospector.mysqlType("varchar", "varchar(20)", 20L, null, null)).isEqualTo("VARCHAR(20)");
        assertThat(SchemaIntrospector.mysqlType("decimal", "decimal(10,2)", null, 10L, 2L)).isEqualTo("DECIMAL(10,2)");
        assertThat(SchemaIntrospector.mysqlType("decimal", "decimal(12,2) unsigned", null, 12L, 2L))
                .isEqualTo("DECIMAL(12,2) UNSIGNED");
        assertThat(SchemaIntrospector.mysqlType("int", "int(11)", null, 10L, 0L)).isEqualTo("INT");
        assertThat(SchemaIntrospector.mysqlType("int", "int(10) unsigned", null, 10L, 0L)).isEqualTo("INT UNSIGNED");
        assertThat(SchemaIntrospector.mysqlType("enum", "enum('a','b')", 1L, null, null)).isEqualTo("ENUM");
        assertThat(SchemaIntrospector.mysqlType("longtext", "longtext", 4294967295L, null, null)).isEqualTo("LONGTEXT");
    }

    @Test
    void oneSampleQueryPerTable() {
        List<Column> cols = List.of(new Column("stat_cd", "stat_cd", "VARCHAR(2)", true, false, ""),
                new Column("mode_cd", "mode_cd", "VARCHAR(2)", true, false, ""));
        for (Dialect d : Dialect.values()) {
            String sql = new SchemaIntrospector(null, d).sampleUnionSql(d.quote("shipments"), cols);
            assertThat(sql).as(d.name()).contains(" UNION ALL ").startsWith("SELECT 0 AS k, CAST(v AS ");
            assertThat(sql.split(" UNION ALL ")).as(d.name()).hasSize(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MYSQL", "MARIADB"})
    void informationSchemaReaderMatchesJdbcMetadata(String key) throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("NL2SQL_TEST_LIVE")),
                "NL2SQL_TEST_LIVE=true not set (this test rewrites fixture tables)");
        String raw = System.getenv("NL2SQL_TEST_" + key + "_URL");
        Assumptions.assumeTrue(raw != null && !raw.isBlank(), "NL2SQL_TEST_" + key + "_URL not set");
        DatabaseSettings parsed = new DatabaseSettings();
        parsed.setUrl(raw);
        String url = parsed.jdbcUrl();
        String user = System.getenv().getOrDefault("NL2SQL_TEST_" + key + "_USER", parsed.getUsername());
        String password = System.getenv().getOrDefault("NL2SQL_TEST_" + key + "_PASSWORD", parsed.getPassword());
        Dialect d = parsed.dialect();
        try (Connection c = TestDatabase.connect(url, user, password)) {
            TestDatabase.load(c, d);
        }
        try (HikariDataSource ds = new HikariDataSource()) {
            ds.setJdbcUrl(url);
            ds.setUsername(user);
            ds.setPassword(password);
            SchemaCatalog fast = new SchemaIntrospector(ds, d, true)
                    .introspect(null, TestDatabase.TABLES, List.of(), List.of(), true);
            SchemaCatalog jdbc = new SchemaIntrospector(ds, d, false)
                    .introspect(null, TestDatabase.TABLES, List.of(), List.of(), true);
            assertThat(fast.tables().keySet()).containsExactlyElementsOf(jdbc.tables().keySet());
            for (Table j : jdbc.tables().values()) {
                Table f = fast.table(j.name());
                assertThat(f.description()).as(j.name()).isEqualTo(j.description());
                assertThat(names(f.columns())).as(j.name()).containsExactlyElementsOf(names(j.columns()));
                for (Column jc : j.columns()) {
                    Column fc = f.column(jc.name());
                    String where = j.name() + "." + jc.name() + " (" + jc.type() + " / " + fc.type() + ")";
                    assertThat(fc.nullable()).as(where).isEqualTo(jc.nullable());
                    assertThat(fc.primaryKey()).as(where).isEqualTo(jc.primaryKey());
                    assertThat(fc.description()).as(where).isEqualTo(jc.description());
                    assertThat(SchemaIntrospector.isText(fc.type())).as(where).isEqualTo(SchemaIntrospector.isText(jc.type()));
                    assertThat(new TreeSet<>(fc.sampleValues())).as(where).isEqualTo(new TreeSet<>(jc.sampleValues()));
                }
                assertThat(fks(f)).as(j.name()).isEqualTo(fks(j));
            }
            assertThat(fast.table("shipments").column("stat_cd").sampleValues()).contains("D");
        }
    }

    private static List<String> names(List<Column> cols) {
        List<String> out = new ArrayList<>();
        for (Column c : cols) out.add(c.name());
        return out;
    }

    private static TreeSet<String> fks(Table t) {
        TreeSet<String> out = new TreeSet<>();
        for (ForeignKey fk : t.foreignKeys()) out.add(fk.columns() + "->" + fk.refTable() + fk.refColumns());
        return out;
    }
}
