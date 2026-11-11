package io.nl2sql.spring;

import io.nl2sql.core.Nl2SqlEngine;
import io.nl2sql.core.llm.LlmProvider;
import io.nl2sql.core.llm.LlmProviders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Auto-configures a single {@link Nl2SqlEngine} bean from {@code nl2sql.*} properties.
 *
 * <p>If the application context already holds a {@link DataSource} bean, the engine reuses it (so nl2sql queries
 * run through the application's own read-only connection pool) and does not close it on shutdown. Set at least
 * {@code nl2sql.db.type} so the engine knows the SQL dialect to generate and introspect. If no {@code DataSource}
 * is present, the engine opens its own pool from the {@code nl2sql.db.*} properties.
 *
 * <p>The LLM is taken from a {@link LlmProvider} bean if one is defined, otherwise built from the
 * {@code nl2sql.llm.*} properties. Define your own {@code Nl2SqlEngine} bean to override all of this.
 *
 * <p>The core library has no Spring dependency; this module is the only Spring glue.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@ConditionalOnClass(Nl2SqlEngine.class)
@EnableConfigurationProperties(Nl2SqlProperties.class)
public class Nl2SqlAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(Nl2SqlAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public Nl2SqlEngine nl2SqlEngine(Nl2SqlProperties properties, ObjectProvider<DataSource> dataSource,
                                     ObjectProvider<LlmProvider> llmProvider) {
        LlmProvider llm = llmProvider.getIfAvailable(() -> LlmProviders.create(properties.getLlm(), Nl2SqlEngine.defaultJson()));
        DataSource ds = dataSource.getIfAvailable();
        if (ds != null) {
            log.info("nl2sql: reusing the application DataSource; dialect {}", properties.getDb().dialect().productName());
            return new Nl2SqlEngine(properties, ds, llm, false); // do not close a pool we did not create
        }
        log.info("nl2sql: no DataSource bean found; opening an own pool for {}", properties.getDb());
        return Nl2SqlEngine.create(properties, llm);
    }
}
