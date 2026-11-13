package com.kaori.browser.ai.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The complete, closed set of tools the model may use. */
public final class ToolRegistry {

    private final Map<String, BrowserTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new NavigationTools.OpenUrl());
        register(new NavigationTools.Back());
        register(new NavigationTools.Forward());
        register(new NavigationTools.Reload());

        register(new ObservationTools.GetPageInfo());
        register(new ObservationTools.GetDom());
        register(new ObservationTools.GetText());
        register(new ObservationTools.GetLinks());
        register(new ObservationTools.GetForms());
        register(new ObservationTools.GetElements());

        register(new InteractionTools.Click());
        register(new InteractionTools.Type());
        register(new InteractionTools.Clear());
        register(new InteractionTools.Scroll());
        register(new InteractionTools.Select());
        register(new InteractionTools.Wait());
        register(new InteractionTools.Submit());

        register(new DataTools.ExtractTable());
        register(new DataTools.ExtractMarkdown());
        register(new DataTools.ExtractMetadata());
        register(new DataTools.FindText());

        register(new FileTools.Download());
        register(new FileTools.SaveMarkdown());
        register(new FileTools.SavePdf());

        register(new TabTools.NewTab());
        register(new TabTools.SwitchTab());
        register(new TabTools.ListTabs());
        register(new TabTools.CloseTab());
    }

    private void register(BrowserTool tool) {
        if (tool != null && tool.name() != null) {
            tools.put(tool.name(), tool);
        }
    }

    public BrowserTool get(String name) {
        return name == null ? null : tools.get(name);
    }

    public Collection<BrowserTool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public List<String> names() {
        return new ArrayList<>(tools.keySet());
    }

    /** Tool documentation used inside the planner prompt. */
    public String describeForPrompt() {
        StringBuilder builder = new StringBuilder();
        for (BrowserTool tool : tools.values()) {
            builder.append("- ").append(tool.name());
            JSONObject schema = tool.parameters();
            JSONArray required = schema.optJSONArray("required");
            JSONObject properties = schema.optJSONObject("properties");
            List<String> parameterNames = new ArrayList<>();
            if (properties != null) {
                Iterator<String> keys = properties.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    boolean isRequired = required != null && contains(required, key);
                    parameterNames.add(key + (isRequired ? " (required)" : " (optional)"));
                }
            }
            builder.append(parameterNames.isEmpty() ? "()" : "(" + String.join(", ", parameterNames) + ")");
            builder.append(" — ").append(tool.description()).append('\n');
        }
        return builder.toString();
    }

    /** OpenAI-compatible {@code tools} array, used when the model supports it. */
    public JSONArray nativeToolSpecs() {
        JSONArray array = new JSONArray();
        for (BrowserTool tool : tools.values()) {
            JSONObject function = new JSONObject();
            JSONObject wrapper = new JSONObject();
            try {
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.put("parameters", tool.parameters());
                wrapper.put("type", "function");
                wrapper.put("function", function);
            } catch (JSONException ignored) {
                continue;
            }
            array.put(wrapper);
        }
        return array;
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i, ""))) {
                return true;
            }
        }
        return false;
    }
}
