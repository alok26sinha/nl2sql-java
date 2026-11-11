package io.nl2sql.spring;

import io.nl2sql.core.config.Nl2SqlSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds every {@code nl2sql.*} property onto the core {@link Nl2SqlSettings}, e.g. {@code nl2sql.db.type},
 * {@code nl2sql.llm.model}, {@code nl2sql.guardrails.max-rows}. When the application already defines a
 * {@link javax.sql.DataSource}, the engine reuses it and only the non-connection settings
 * ({@code nl2sql.db.type}, schema, include/exclude tables, the LLM and the guardrails) are read.
 */
@ConfigurationProperties(prefix = "nl2sql")
public class Nl2SqlProperties extends Nl2SqlSettings {
}
