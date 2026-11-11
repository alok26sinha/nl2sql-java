package io.nl2sql.spring;

import com.zaxxer.hikari.HikariDataSource;
import io.nl2sql.core.Nl2SqlEngine;
import io.nl2sql.core.llm.LlmProvider;
import io.nl2sql.core.llm.LlmResponse;
import io.nl2sql.core.llm.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Nl2SqlAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Nl2SqlAutoConfiguration.class));

    @TempDir
    Path dir;

    @Test
    void reusesTheApplicationDataSource() {
        DataSource appDs = sqlite(dir.resolve("app.db"));
        runner.withBean(DataSource.class, () -> appDs)
                .withPropertyValues("nl2sql.db.type=sqlite", "nl2sql.db.name=" + dir.resolve("app.db"))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Nl2SqlEngine.class);
                    Nl2SqlEngine engine = ctx.getBean(Nl2SqlEngine.class);
                    assertThat(engine.dataSource()).isSameAs(appDs); // the app's pool, reused
                });
    }

    @Test
    void doesNotCloseABorrowedDataSource() {
        // The auto-config builds the engine with ownsDataSource=false, so closing the engine leaves the
        // application's pool open (the application owns its own DataSource lifecycle).
        HikariDataSource appDs = (HikariDataSource) sqlite(dir.resolve("borrowed.db"));
        io.nl2sql.core.config.Nl2SqlSettings s = new io.nl2sql.core.config.Nl2SqlSettings();
        s.getDb().setType("sqlite");
        s.getDb().setName(dir.resolve("borrowed.db").toString());
        LlmProvider noop = messages -> new LlmResponse("", "noop");
        try (Nl2SqlEngine engine = new Nl2SqlEngine(s, appDs, noop, false)) {
            assertThat(engine.dataSource()).isSameAs(appDs);
        }
        assertThat(appDs.isClosed()).isFalse();
        appDs.close();
    }

    @Test
    void opensAnOwnPoolWhenNoDataSourceBean() {
        runner.withPropertyValues("nl2sql.db.type=sqlite", "nl2sql.db.name=" + dir.resolve("own.db"))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Nl2SqlEngine.class);
                    assertThat(ctx.getBean(Nl2SqlEngine.class).dataSource()).isInstanceOf(HikariDataSource.class);
                });
    }

    @Test
    void bindsNl2SqlProperties() {
        runner.withPropertyValues(
                        "nl2sql.db.type=sqlite", "nl2sql.db.name=" + dir.resolve("props.db"),
                        "nl2sql.llm.model=my-model", "nl2sql.guardrails.max-rows=42")
                .run(ctx -> {
                    Nl2SqlProperties props = ctx.getBean(Nl2SqlProperties.class);
                    assertThat(props.getLlm().getModel()).isEqualTo("my-model");
                    assertThat(props.getGuardrails().getMaxRows()).isEqualTo(42);
                });
    }

    @Test
    void usesAnLlmProviderBeanWhenPresent() {
        LlmProvider custom = new LlmProvider() {
            @Override
            public LlmResponse complete(List<Message> messages) {
                return new LlmResponse("", "custom");
            }
        };
        runner.withBean(LlmProvider.class, () -> custom)
                .withPropertyValues("nl2sql.db.type=sqlite", "nl2sql.db.name=" + dir.resolve("llm.db"))
                .run(ctx -> assertThat(ctx.getBean(Nl2SqlEngine.class).llm()).isSameAs(custom));
    }

    @Test
    void backsOffWhenAnEngineBeanIsAlreadyDefined() {
        Nl2SqlEngine existing = org.mockito.Mockito.mock(Nl2SqlEngine.class);
        runner.withBean("myEngine", Nl2SqlEngine.class, () -> existing)
                .withPropertyValues("nl2sql.db.type=sqlite", "nl2sql.db.name=" + dir.resolve("x.db"))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Nl2SqlEngine.class);
                    assertThat(ctx.getBean(Nl2SqlEngine.class)).isSameAs(existing);
                });
    }

    private static DataSource sqlite(Path file) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:sqlite:" + file);
        ds.setMaximumPoolSize(1);
        return ds;
    }
}
