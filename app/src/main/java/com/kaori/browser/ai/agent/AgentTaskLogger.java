package com.kaori.browser.ai.agent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Real execution trace; never synthesized. */
public final class AgentTaskLogger {

    private static final int MAX_ENTRIES = 400;

    private final List<AgentLogEntry> entries = new ArrayList<>();

    public void log(int step, String tool, String status, String detail, String url) {
        entries.add(new AgentLogEntry(step, tool, status, detail, url));
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    public List<AgentLogEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public String toPlainText() {
        StringBuilder builder = new StringBuilder();
        for (AgentLogEntry entry : entries) {
            builder.append(entry.describe());
            if (!entry.url.isEmpty()) {
                builder.append(" [").append(entry.url).append(']');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public JSONArray toJson() {
        JSONArray array = new JSONArray();
        for (AgentLogEntry entry : entries) {
            JSONObject object = new JSONObject();
            try {
                object.put("step", entry.step);
                object.put("tool", entry.tool);
                object.put("status", entry.status);
                object.put("detail", entry.detail);
                object.put("url", entry.url);
                object.put("at", entry.timestamp);
            } catch (Exception ignored) {
            }
            array.put(object);
        }
        return array;
    }

    public void restoreFromJson(JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                entries.add(new AgentLogEntry(
                        object.optInt("step", 0),
                        object.optString("tool", ""),
                        object.optString("status", ""),
                        object.optString("detail", ""),
                        object.optString("url", "")));
            }
        }
    }
}
