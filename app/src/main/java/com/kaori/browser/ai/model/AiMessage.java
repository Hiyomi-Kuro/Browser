package com.kaori.browser.ai.model;

import org.json.JSONException;
import org.json.JSONObject;

/** One chat message in the provider-neutral format. */
public final class AiMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    private final String role;
    private final String content;
    private final String toolCallId;

    public AiMessage(String role, String content) {
        this(role, content, null);
    }

    public AiMessage(String role, String content, String toolCallId) {
        this.role = role == null ? ROLE_USER : role;
        this.content = content == null ? "" : content;
        this.toolCallId = toolCallId;
    }

    public static AiMessage system(String content) {
        return new AiMessage(ROLE_SYSTEM, content);
    }

    public static AiMessage user(String content) {
        return new AiMessage(ROLE_USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(ROLE_ASSISTANT, content);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("role", role);
        object.put("content", content);
        if (toolCallId != null && !toolCallId.isEmpty()) {
            object.put("tool_call_id", toolCallId);
        }
        return object;
    }
}
