package com.kaori.browser.ai.agent;

/** One completed Observe → Plan → Act → Verify iteration. */
public final class AgentStep {

    public final int index;
    public final String thought;
    public final String tool;
    public final String argumentsSummary;
    public final boolean ok;
    public final String message;
    public final String urlAfter;
    public final long durationMs;
    public final boolean needsConfirmation;

    public AgentStep(int index, String thought, String tool, String argumentsSummary,
                     boolean ok, String message, String urlAfter, long durationMs,
                     boolean needsConfirmation) {
        this.index = index;
        this.thought = thought == null ? "" : thought;
        this.tool = tool == null ? "" : tool;
        this.argumentsSummary = argumentsSummary == null ? "" : argumentsSummary;
        this.ok = ok;
        this.message = message == null ? "" : message;
        this.urlAfter = urlAfter == null ? "" : urlAfter;
        this.durationMs = durationMs;
        this.needsConfirmation = needsConfirmation;
    }

    public String describeForPrompt() {
        return index + ". " + tool + " " + argumentsSummary + " → "
                + (ok ? "ok" : "failed") + ": " + message;
    }
}
