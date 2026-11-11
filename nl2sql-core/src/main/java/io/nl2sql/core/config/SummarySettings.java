package io.nl2sql.core.config;

/**
 * Optional plain-English summary of the result rows. Off by default, and deliberately so: when enabled the
 * returned rows are sent to the LLM for a one-paragraph summary. Ordinarily only the schema goes to the LLM
 * (table and column names, and a few sample values), never the rows a query returns.
 */
public class SummarySettings {
    /** When true, ask the LLM for a short plain-English summary of the rows. Off by default; it sends result rows to the LLM. */
    private boolean enabled = false;
    /** At most this many rows are sent to the LLM for the summary. */
    private int maxRows = 50;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = Math.max(1, maxRows); }
}
