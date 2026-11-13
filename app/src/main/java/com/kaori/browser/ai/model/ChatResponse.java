package com.kaori.browser.ai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provider-neutral chat response. */
public final class ChatResponse {

    private String content = "";
    private String reasoning = "";
    private String finishReason = "";
    private String modelName = "";
    private int promptTokens;
    private int completionTokens;
    private final List<ToolCall> toolCalls = new ArrayList<>();

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    public void appendContent(String delta) {
        if (delta != null && !delta.isEmpty()) {
            this.content = this.content + delta;
        }
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning == null ? "" : reasoning;
    }

    public void appendReasoning(String delta) {
        if (delta != null && !delta.isEmpty()) {
            this.reasoning = this.reasoning + delta;
        }
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason == null ? "" : finishReason;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName == null ? "" : modelName;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = Math.max(0, promptTokens);
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = Math.max(0, completionTokens);
    }

    public List<ToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }

    public void addToolCall(ToolCall call) {
        if (call != null) {
            toolCalls.add(call);
        }
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.trim().isEmpty();
    }

    public String structuralHint() {
        return "content=" + (content == null ? 0 : content.length())
                + " reasoning=" + (reasoning == null ? 0 : reasoning.length())
                + " finish_reason=" + (finishReason.isEmpty() ? "-" : finishReason)
                + " tool_calls=" + toolCalls.size();
    }
}
