package com.kaori.browser.ai.agent;

import com.kaori.browser.ai.agent.tools.BrowserTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Local schema validation for a model-proposed action.
 *
 * <p>Nothing is executed unless the tool exists in the registry, every required
 * argument is present, each argument matches the declared type/enum, and the tool's own
 * argument rules accept it.</p>
 */
public final class ActionValidator {

    public static final class Result {
        public final boolean ok;
        public final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }

        public static Result ok() {
            return new Result(true, "");
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public Result validate(BrowserTool tool, JSONObject arguments) {
        if (tool == null) {
            return Result.fail("Unknown tool. Use one of the tool names from the instructions.");
        }
        JSONObject args = arguments == null ? new JSONObject() : arguments;
        JSONObject schema = tool.parameters();
        JSONObject properties = schema == null ? null : schema.optJSONObject("properties");
        JSONArray required = schema == null ? null : schema.optJSONArray("required");

        if (required != null) {
            for (int i = 0; i < required.length(); i++) {
                String name = required.optString(i, "");
                if (name.isEmpty()) {
                    continue;
                }
                if (!hasUsableValue(args, name)) {
                    return Result.fail("Missing required argument '" + name + "' for "
                            + tool.name() + ".");
                }
            }
        }

        if (properties != null) {
            Iterator<String> keys = properties.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                if (!args.has(name) || args.isNull(name)) {
                    continue;
                }
                JSONObject property = properties.optJSONObject(name);
                if (property == null) {
                    continue;
                }
                String type = property.optString("type", "string");
                if (!typeMatches(type, args.opt(name))) {
                    return Result.fail("Argument '" + name + "' of " + tool.name()
                            + " must be of type " + type + ".");
                }
                JSONArray allowed = property.optJSONArray("enum");
                if (allowed != null && !enumContains(allowed, args.opt(name))) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < allowed.length(); i++) {
                        if (i > 0) {
                            builder.append(", ");
                        }
                        builder.append(allowed.optString(i, ""));
                    }
                    return Result.fail("Argument '" + name + "' must be one of: "
                            + builder + ".");
                }
            }
        }

        String extra = tool.validateArguments(args);
        if (extra != null && !extra.trim().isEmpty()) {
            return Result.fail(extra.trim());
        }
        return Result.ok();
    }

    private static boolean hasUsableValue(JSONObject args, String name) {
        if (!args.has(name) || args.isNull(name)) {
            return false;
        }
        Object value = args.opt(name);
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }
        return value != null;
    }

    /** Tolerant type check: numeric strings are accepted for integer/number fields. */
    public static boolean typeMatches(String type, Object value) {
        if (value == null || value == JSONObject.NULL) {
            return false;
        }
        switch (type) {
            case "string":
                return value instanceof String;
            case "integer":
            case "number":
                if (value instanceof Number) {
                    return true;
                }
                if (value instanceof String) {
                    try {
                        Double.parseDouble(((String) value).trim());
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return false;
            case "boolean":
                if (value instanceof Boolean) {
                    return true;
                }
                if (value instanceof String) {
                    String text = ((String) value).trim().toLowerCase(java.util.Locale.US);
                    return "true".equals(text) || "false".equals(text);
                }
                return false;
            case "array":
                return value instanceof JSONArray;
            case "object":
                return value instanceof JSONObject;
            default:
                return true;
        }
    }

    private static boolean enumContains(JSONArray allowed, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        for (int i = 0; i < allowed.length(); i++) {
            if (text.equals(allowed.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }
}
