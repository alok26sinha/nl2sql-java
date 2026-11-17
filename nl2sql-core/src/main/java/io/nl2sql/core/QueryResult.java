package io.nl2sql.core;

import java.util.List;

/**
 * Outcome of one question. Exactly one of these holds: {@code sql != null && error == null} (success),
 * {@code clarification != null} (the question was ambiguous), or {@code error != null}.
 * {@code columns}/{@code rows} are empty when the SQL was only generated, not run.
 * {@code summary} is a plain-English description of the rows, present only when result summaries are enabled
 * (off by default; see {@code nl2sql.summary.enabled}), otherwise {@code null}.
 */
public record QueryResult(
        String question,
        String sql,
        String explanation,
        String clarification,
        List<String> tablesUsed,
        int attempts,
        String error,
        List<String> columns,
        List<List<Object>> rows,
        boolean truncated,
        double elapsedMs,
        boolean executed,
        String summary) {

    public boolean ok() {
        return sql != null && error == null;
    }

    public int rowCount() {
        return rows.size();
    }

    /** A copy of this result with the plain-English row summary set. */
    public QueryResult withSummary(String summary) {
        return new QueryResult(question, sql, explanation, clarification, tablesUsed, attempts, error, columns, rows,
                truncated, elapsedMs, executed, summary);
    }

    static QueryResult failure(String question, String explanation, int attempts, String error) {
        return new QueryResult(question, null, explanation, null, List.of(), attempts, error, List.of(), List.of(), false, 0, false, null);
    }

    static QueryResult clarification(String question, String explanation, String clarification, int attempts) {
        return new QueryResult(question, null, explanation, clarification, List.of(), attempts, null, List.of(), List.of(), false, 0, false, null);
    }
}
