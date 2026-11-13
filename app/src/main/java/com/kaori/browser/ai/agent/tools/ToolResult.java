package com.kaori.browser.ai.agent.tools;

import com.kaori.browser.ai.reader.ContentBudget;

/** Result of one tool invocation, including whether the page state changed. */
public final class ToolResult {

    public boolean ok;
    public String message;
    public String data;
    public boolean pageChanged;
    public String urlBefore;
    public String urlAfter;
    public boolean userVisibleOnly;

    private ToolResult(boolean ok, String message, String data) {
        this.ok = ok;
        this.message = message == null ? "" : message;
        this.data = data == null ? "" : data;
        this.urlBefore = "";
        this.urlAfter = "";
    }

    public static ToolResult ok(String message) {
        return new ToolResult(true, message, "");
    }

    public static ToolResult ok(String message, String data) {
        return new ToolResult(true, message, data);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, message, "");
    }

    public ToolResult withData(String data) {
        this.data = data == null ? "" : data;
        return this;
    }

    public ToolResult withPageChange(String before, String after) {
        this.urlBefore = before == null ? "" : before;
        this.urlAfter = after == null ? "" : after;
        this.pageChanged = !this.urlBefore.equals(this.urlAfter);
        return this;
    }

    public ToolResult markPageChanged(boolean changed) {
        this.pageChanged = changed;
        return this;
    }

    /** Full text handed back to the model, bounded so one tool cannot flood the context. */
    public String toModelText(int maxChars) {
        StringBuilder builder = new StringBuilder();
        builder.append(ok ? "OK" : "ERROR").append(": ").append(message);
        if (pageChanged) {
            builder.append("\nPage changed: ").append(urlBefore.isEmpty() ? "(previous)" : urlBefore)
                    .append(" → ").append(urlAfter.isEmpty() ? "(unknown)" : urlAfter);
        }
        if (!data.isEmpty()) {
            builder.append("\n\n").append(ContentBudget.trim(data, maxChars));
        }
        return builder.toString();
    }
}
