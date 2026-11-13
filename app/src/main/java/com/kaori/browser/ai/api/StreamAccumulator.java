package com.kaori.browser.ai.api;

import com.kaori.browser.ai.model.ChatResponse;
import com.kaori.browser.ai.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Accumulates incremental SSE payloads into one {@link ChatResponse}. */
public final class StreamAccumulator {

    private final Map<Integer, ToolCallDraft> toolCallDrafts = new LinkedHashMap<>();

    public void accept(String payload, ChatResponse response, AiClient.StreamListener listener)
            throws AiException {
        if (payload == null) {
            return;
        }
        String trimmed = payload.trim();
        if (trimmed.isEmpty() || "[DONE]".equals(trimmed)) {
            return;
        }

        JSONObject chunk;
        try {
            chunk = new JSONObject(trimmed);
        } catch (JSONException e) {
            // Keep-alive comments and partial frames are not fatal.
            return;
        }

        Object error = chunk.opt("error");
        if (error != null && error != JSONObject.NULL) {
            String message = AiResponseParser.errorMessage(chunk);
            throw new AiException(AiResponseParser.classify(message), 0,
                    message.isEmpty() ? "Streaming request failed" : message);
        }

        String model = chunk.optString("model", "");
        if (!model.isEmpty()) {
            response.setModelName(model);
        }
        AiResponseParser.mergeUsage(chunk, response);

        JSONArray choices = chunk.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            if (choice != null) {
                String finish = choice.optString("finish_reason", "");
                if (!finish.isEmpty() && !"null".equals(finish)) {
                    response.setFinishReason(finish);
                }
                JSONObject delta = choice.optJSONObject("delta");
                if (delta == null) {
                    delta = choice.optJSONObject("message");
                }
                if (delta != null) {
                    acceptDelta(delta, response, listener);
                }
            }
            return;
        }

        String type = chunk.optString("type", "");
        if ("content_block_delta".equals(type)) {
            JSONObject delta = chunk.optJSONObject("delta");
            if (delta != null) {
                emitText(delta.optString("text", ""), response, listener);
            }
            return;
        }
        if ("message_delta".equals(type)) {
            JSONObject delta = chunk.optJSONObject("delta");
            if (delta != null) {
                String stop = delta.optString("stop_reason", "");
                if (!stop.isEmpty()) {
                    response.setFinishReason(stop);
                }
            }
            return;
        }

        JSONArray candidates = chunk.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.optJSONObject(0);
            if (candidate != null) {
                JSONObject content = candidate.optJSONObject("content");
                if (content != null) {
                    JSONArray parts = content.optJSONArray("parts");
                    if (parts != null) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.optJSONObject(i);
                            if (part != null) {
                                emitText(part.optString("text", ""), response, listener);
                            }
                        }
                    }
                }
            }
            return;
        }

        JSONObject genericDelta = chunk.optJSONObject("delta");
        if (genericDelta != null) {
            emitText(genericDelta.optString("text", ""), response, listener);
        }
    }

    private void acceptDelta(JSONObject delta, ChatResponse response,
                             AiClient.StreamListener listener) {
        emitText(AiResponseParser.extractText(delta.opt("content")), response, listener);

        String reasoning = AiResponseParser.firstNonEmpty(
                delta.optString("reasoning_content", ""),
                delta.optString("reasoning", ""));
        if (!reasoning.isEmpty()) {
            response.appendReasoning(reasoning);
            if (listener != null) {
                listener.onReasoningDelta(reasoning);
            }
        }

        JSONArray calls = delta.optJSONArray("tool_calls");
        if (calls == null) {
            return;
        }
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.optJSONObject(i);
            if (call == null) {
                continue;
            }
            int index = call.optInt("index", i);
            ToolCallDraft draft = toolCallDrafts.get(index);
            if (draft == null) {
                draft = new ToolCallDraft();
                toolCallDrafts.put(index, draft);
            }
            String id = call.optString("id", "");
            if (!id.isEmpty()) {
                draft.id = id;
            }
            JSONObject function = call.optJSONObject("function");
            if (function != null) {
                String name = function.optString("name", "");
                if (!name.isEmpty()) {
                    draft.name = name;
                }
                String arguments = function.optString("arguments", "");
                if (!arguments.isEmpty()) {
                    draft.arguments.append(arguments);
                }
            }
        }
    }

    private void emitText(String text, ChatResponse response, AiClient.StreamListener listener) {
        if (text == null || text.isEmpty()) {
            return;
        }
        response.appendContent(text);
        if (listener != null) {
            listener.onTextDelta(text);
        }
    }

    /** Moves accumulated streamed tool calls onto the response. */
    public void finish(ChatResponse response) {
        int index = 0;
        for (Map.Entry<Integer, ToolCallDraft> entry : toolCallDrafts.entrySet()) {
            ToolCallDraft draft = entry.getValue();
            if (draft.name.isEmpty()) {
                continue;
            }
            String arguments = draft.arguments.toString().trim();
            response.addToolCall(new ToolCall(
                    draft.id.isEmpty() ? "call_stream_" + index : draft.id,
                    draft.name,
                    arguments.isEmpty() ? "{}" : arguments));
            index++;
        }
    }

    private static final class ToolCallDraft {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();
    }
}
