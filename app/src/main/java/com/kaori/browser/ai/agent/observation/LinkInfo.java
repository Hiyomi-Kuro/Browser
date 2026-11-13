package com.kaori.browser.ai.agent.observation;

import org.json.JSONObject;

/** One link on the page. */
public final class LinkInfo {

    public final String text;
    public final String href;

    public LinkInfo(String text, String href) {
        this.text = text == null ? "" : text;
        this.href = href == null ? "" : href;
    }

    public static LinkInfo fromJson(JSONObject json) {
        return new LinkInfo(json.optString("text", ""), json.optString("href", ""));
    }

    public String describe() {
        return "- " + (text.isEmpty() ? "(no text)" : text) + " → " + href;
    }
}
