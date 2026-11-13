package com.kaori.browser.ai.agent.observation;

import org.json.JSONObject;

/** One interactable DOM element from a page snapshot. */
public final class ElementInfo {

    public final String elementId;
    public final String tag;
    public final String role;
    public final String type;
    public final String text;
    public final String label;
    public final String name;
    public final String placeholder;
    public final String href;
    public final String value;
    public final String selector;
    public final boolean disabled;
    public final boolean sensitive;
    public final boolean hasValue;
    public final boolean inViewport;

    public ElementInfo(String elementId, String tag, String role, String type, String text,
                       String label, String name, String placeholder, String href, String value,
                       String selector, boolean disabled, boolean sensitive, boolean hasValue,
                       boolean inViewport) {
        this.elementId = elementId;
        this.tag = tag;
        this.role = role;
        this.type = type;
        this.text = text;
        this.label = label;
        this.name = name;
        this.placeholder = placeholder;
        this.href = href;
        this.value = value;
        this.selector = selector;
        this.disabled = disabled;
        this.sensitive = sensitive;
        this.hasValue = hasValue;
        this.inViewport = inViewport;
    }

    public static ElementInfo fromJson(JSONObject json, int fallbackIndex) {
        return new ElementInfo(
                json.optString("element_id", "e" + fallbackIndex),
                json.optString("tag", ""),
                json.optString("role", ""),
                json.optString("type", ""),
                json.optString("text", ""),
                json.optString("label", ""),
                json.optString("name", ""),
                json.optString("placeholder", ""),
                json.optString("href", ""),
                json.optString("value", ""),
                json.optString("selector", ""),
                json.optBoolean("disabled", false),
                json.optBoolean("sensitive", false),
                json.optBoolean("has_value", false),
                json.optBoolean("in_viewport", true));
    }

    public boolean isInput() {
        return "input".equals(tag) || "textarea".equals(tag) || "select".equals(tag)
                || "true".equals(role) || "textbox".equals(role) || "searchbox".equals(role);
    }

    public boolean isPassword() {
        return "password".equals(type);
    }

    public String displayText() {
        if (!label.isEmpty()) {
            return label;
        }
        if (!text.isEmpty()) {
            return text;
        }
        if (!placeholder.isEmpty()) {
            return placeholder;
        }
        return href.isEmpty() ? name : href;
    }

    /** Compact one-line description used in the model prompt. */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append(elementId).append(' ').append(tag);
        if (!type.isEmpty()) {
            builder.append('[').append(type).append(']');
        }
        if (!role.isEmpty()) {
            builder.append(" role=").append(role);
        }
        String display = displayText();
        if (!display.isEmpty()) {
            builder.append(" \"").append(trim(display, 70)).append('"');
        }
        if (!name.isEmpty()) {
            builder.append(" name=").append(trim(name, 30));
        }
        if (sensitive) {
            builder.append(" [sensitive input: value withheld]");
        } else if (hasValue && !value.isEmpty()) {
            builder.append(" value=\"").append(trim(value, 40)).append('"');
        }
        if (disabled) {
            builder.append(" [disabled]");
        }
        if (!inViewport) {
            builder.append(" [offscreen]");
        }
        return builder.toString();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String single = value.replace('\n', ' ').replace('\r', ' ').trim();
        return single.length() <= max ? single : single.substring(0, max) + "…";
    }
}
