package com.kaori.browser.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small builders for tool JSON schemas. */
public final class ToolSchemas {

    private ToolSchemas() {
    }

    public static JSONObject schema(JSONObject properties, String... required) {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", properties == null ? new JSONObject() : properties);
            JSONArray array = new JSONArray();
            if (required != null) {
                for (String name : required) {
                    array.put(name);
                }
            }
            schema.put("required", array);
        } catch (JSONException ignored) {
            // Schemas are built from constants; failure is impossible in practice.
        }
        return schema;
    }

    public static JSONObject property(String type, String description) {
        JSONObject property = new JSONObject();
        try {
            property.put("type", type);
            property.put("description", description);
        } catch (JSONException ignored) {
        }
        return property;
    }

    public static JSONObject stringProperty(String description) {
        return property("string", description);
    }

    public static JSONObject booleanProperty(String description) {
        return property("boolean", description);
    }

    public static JSONObject integerProperty(String description, int min, int max) {
        JSONObject property = property("integer", description);
        try {
            property.put("minimum", min);
            property.put("maximum", max);
        } catch (JSONException ignored) {
        }
        return property;
    }

    public static JSONObject enumProperty(String description, String... values) {
        JSONObject property = property("string", description);
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        try {
            property.put("enum", array);
        } catch (JSONException ignored) {
        }
        return property;
    }

    /** Builds a properties object from alternating name/schema arguments. */
    public static JSONObject properties(Object... nameThenSchema) {
        JSONObject properties = new JSONObject();
        for (int i = 0; i + 1 < nameThenSchema.length; i += 2) {
            Object name = nameThenSchema[i];
            Object value = nameThenSchema[i + 1];
            if (name instanceof String && value instanceof JSONObject) {
                try {
                    properties.put((String) name, value);
                } catch (JSONException ignored) {
                }
            }
        }
        return properties;
    }
}
