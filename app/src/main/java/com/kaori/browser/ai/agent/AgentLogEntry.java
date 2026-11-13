package com.kaori.browser.ai.agent;

/** One line of the real execution log surfaced in the UI and saved with the task. */
public final class AgentLogEntry {

    public final int step;
    public final String tool;
    public final String status;
    public final String detail;
    public final String url;
    public final long timestamp;

    public AgentLogEntry(int step, String tool, String status, String detail, String url) {
        this.step = step;
        this.tool = tool == null ? "" : tool;
        this.status = status == null ? "" : status;
        this.detail = detail == null ? "" : detail;
        this.url = url == null ? "" : url;
        this.timestamp = System.currentTimeMillis();
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        if (step > 0) {
            builder.append("step ").append(step).append(" · ");
        }
        builder.append(status);
        if (!tool.isEmpty()) {
            builder.append(" · ").append(tool);
        }
        if (!detail.isEmpty()) {
            builder.append(" · ").append(detail);
        }
        return builder.toString();
    }
}
