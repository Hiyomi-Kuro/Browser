package com.kaori.browser.ai.agent;

/** Snapshot of one browser tab, safe to hand to the model. */
public final class TabInfo {

    public final int index;
    public final String title;
    public final String url;
    public final boolean agentCreated;
    public final boolean active;

    public TabInfo(int index, String title, String url, boolean agentCreated, boolean active) {
        this.index = index;
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.agentCreated = agentCreated;
        this.active = active;
    }

    public String describe() {
        return index + (active ? "*" : "") + (agentCreated ? " (agent)" : " (user)")
                + " " + (title.isEmpty() ? url : title)
                + (url.isEmpty() || url.equals(title) ? "" : " — " + url);
    }
}
