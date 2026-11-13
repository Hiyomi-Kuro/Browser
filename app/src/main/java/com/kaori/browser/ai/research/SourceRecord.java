package com.kaori.browser.ai.research;

/** A page actually visited by the agent, kept so the report can cite it. */
public final class SourceRecord {

    public final String url;
    public final String title;
    public final long accessedAt;
    public String excerpt;
    public String status;

    public SourceRecord(String url, String title, String excerpt, String status) {
        this.url = url == null ? "" : url;
        this.title = title == null ? "" : title;
        this.excerpt = excerpt == null ? "" : excerpt;
        this.status = status == null ? "read" : status;
        this.accessedAt = System.currentTimeMillis();
    }

    public String describe() {
        return (title.isEmpty() ? url : title) + " — " + url + " (" + status + ")";
    }
}
