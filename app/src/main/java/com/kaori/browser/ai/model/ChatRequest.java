package com.kaori.browser.ai.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provider-neutral chat request. */
public final class ChatRequest {

    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final boolean stream;
    private final String sessionId;
    private final JSONArray tools;
    private final List<AiMessage> messages = new ArrayList<>();

    public ChatRequest(String model, double temperature, int maxTokens, boolean stream,
                       String sessionId, JSONArray tools) {
        this.model = model == null ? "" : model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.stream = stream;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.tools = tools;
    }

    public ChatRequest add(AiMessage message) {
        if (message != null) {
            messages.add(message);
        }
        return this;
    }

    public ChatRequest addSystem(String content) {
        return add(AiMessage.system(content));
    }

    public ChatRequest addUser(String content) {
        return add(AiMessage.user(content));
    }

    public ChatRequest addAssistant(String content) {
        return add(AiMessage.assistant(content));
    }

    public String getModel() {
        return model;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isStream() {
        return stream;
    }

    public List<AiMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    /** Same request with a different output budget, used for the truncation retry. */
    public ChatRequest withMaxTokens(int newMaxTokens) {
        ChatRequest copy = new ChatRequest(model, temperature, newMaxTokens, stream,
                sessionId, tools);
        for (AiMessage message : messages) {
            copy.add(message);
        }
        return copy;
    }

    public JSONObject toJson(boolean includeTemperature) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("model", model);
        object.put("stream", stream);

        if (stream) {
            // Ask OpenAI-compatible servers for the usage block inside the final chunk.
            JSONObject streamOptions = new JSONObject();
            streamOptions.put("include_usage", true);
            object.put("stream_options", streamOptions);
        }

        JSONArray array = new JSONArray();
        for (AiMessage message : messages) {
            array.put(message.toJson());
        }
        object.put("messages", array);

        if (maxTokens > 0) {
            object.put("max_tokens", maxTokens);
        }
        if (includeTemperature && !Double.isNaN(temperature) && temperature >= 0) {
            object.put("temperature", temperature);
        }
        if (tools != null && tools.length() > 0) {
            object.put("tools", tools);
            object.put("tool_choice", "auto");
        }
        return object;
    }
}
