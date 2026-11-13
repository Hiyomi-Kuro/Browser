package com.kaori.browser.ai.agent.observation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One form on the page with its field structure. */
public final class FormInfo {

    public final int index;
    public final String action;
    public final String method;
    public final List<Field> fields;

    public FormInfo(int index, String action, String method, List<Field> fields) {
        this.index = index;
        this.action = action == null ? "" : action;
        this.method = method == null ? "get" : method;
        this.fields = fields == null ? new ArrayList<>() : fields;
    }

    public static FormInfo fromJson(JSONObject json, int fallbackIndex) {
        List<Field> fields = new ArrayList<>();
        JSONArray array = json.optJSONArray("fields");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject field = array.optJSONObject(i);
                if (field != null) {
                    fields.add(new Field(
                            field.optString("tag", ""),
                            field.optString("type", ""),
                            field.optString("name", ""),
                            field.optString("placeholder", ""),
                            field.optBoolean("required", false)));
                }
            }
        }
        return new FormInfo(json.optInt("index", fallbackIndex),
                json.optString("action", ""),
                json.optString("method", "get"),
                fields);
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("form #").append(index).append(" (").append(method.toUpperCase())
                .append(' ').append(action.isEmpty() ? "(no action)" : action).append(")");
        for (Field field : fields) {
            builder.append("\n  - ").append(field.describe());
        }
        return builder.toString();
    }

    public static final class Field {
        public final String tag;
        public final String type;
        public final String name;
        public final String placeholder;
        public final boolean required;

        public Field(String tag, String type, String name, String placeholder, boolean required) {
            this.tag = tag;
            this.type = type;
            this.name = name;
            this.placeholder = placeholder;
            this.required = required;
        }

        public String describe() {
            StringBuilder builder = new StringBuilder(tag);
            if (!type.isEmpty()) {
                builder.append('[').append(type).append(']');
            }
            if (!name.isEmpty()) {
                builder.append(" name=").append(name);
            }
            if (!placeholder.isEmpty()) {
                builder.append(" placeholder=\"").append(placeholder).append('"');
            }
            if (required) {
                builder.append(" [required]");
            }
            return builder.toString();
        }
    }
}
