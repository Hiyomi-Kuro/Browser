package com.kaori.browser.ai.api;

import com.kaori.browser.ai.model.ChatResponse;
import com.kaori.browser.ai.model.ToolCall;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tolerant parser for the response shapes an "OpenAI-compatible" endpoint may return.
 *
 * <p>Catalogued shapes: OpenAI chat completions (string or content-part arrays),
 * OpenAI Responses ({@code output_text}/{@code output}), Anthropic Messages
 * ({@code content} blocks) and Gemini {@code candidates[].content.parts}. Error
 * envelopes are surfaced with the provider's own message.</p>
 */
public final class AiResponseParser {

    private AiResponseParser() {
    }

    public static ChatResponse parseChat(String body) throws AiException {
        if (body == null || body.trim().isEmpty()) {
            throw new AiException(AiException.Kind.PARSE, 200, "Response body is empty");
        }
        JSONObject root;
        try {
            root = new JSONObject(body.trim());
        } catch (JSONException e) {
            throw new AiException(AiException.Kind.PARSE, 200,
                    "Response is not valid JSON", e);
        }
        return parseChatObject(root);
    }

    public static ChatResponse parseChatObject(JSONObject root) throws AiException {
        if (root == null) {
            throw new AiException(AiException.Kind.PARSE, 200, "Response is empty");
        }

        Object error = root.opt("error");
        if (error != null && error != JSONObject.NULL) {
            String message = errorMessage(root);
            throw new AiException(classify(message), 0,
                    message.isEmpty() ? "Provider returned an error" : message);
        }

        ChatResponse response = new ChatResponse();
        response.setModelName(root.optString("model", ""));

        JSONArray choices = root.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject choice = choices.optJSONObject(0);
            if (choice != null) {
                response.setFinishReason(choice.optString("finish_reason", ""));
                JSONObject message = choice.optJSONObject("message");
                if (message == null) {
                    message = choice.optJSONObject("delta");
                }
                if (message != null) {
                    response.setContent(extractText(message.opt("content")));
                    response.setReasoning(firstNonEmpty(
                            message.optString("reasoning_content", ""),
                            message.optString("reasoning", "")));
                    List<ToolCall> calls = parseToolCalls(message.optJSONArray("tool_calls"));
                    for (ToolCall call : calls) {
                        response.addToolCall(call);
                    }
                }
                if (!response.hasContent()) {
                    String legacy = choice.optString("text", "");
                    if (!legacy.isEmpty()) {
                        response.setContent(legacy);
                    }
                }
            }
        }

        if (!response.hasContent()) {
            Object outputText = root.opt("output_text");
            if (outputText instanceof String) {
                response.setContent((String) outputText);
            }
        }

        if (!response.hasContent()) {
            JSONArray output = root.optJSONArray("output");
            if (output != null) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String type = item.optString("type", "message");
                    if (!"message".equals(type) && !"output_text".equals(type)) {
                        continue;
                    }
                    builder.append(extractText(item.opt("content")));
                }
                if (builder.length() > 0) {
                    response.setContent(builder.toString());
                }
            }
        }

        if (!response.hasContent() && root.optJSONArray("content") != null) {
            JSONArray content = root.optJSONArray("content");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.optJSONObject(i);
                if (block == null) {
                    continue;
                }
                String type = block.optString("type", "");
                if ("text".equals(type) || type.isEmpty()) {
                    builder.append(block.optString("text", ""));
                } else if ("tool_use".equals(type)) {
                    Object input = block.opt("input");
                    response.addToolCall(new ToolCall(
                            block.optString("id", ""),
                            block.optString("name", ""),
                            input == null || input == JSONObject.NULL ? "{}" : input.toString()));
                }
            }
            response.setContent(builder.toString());
            String stop = root.optString("stop_reason", "");
            if (!stop.isEmpty()) {
                response.setFinishReason(stop);
            }
        }

        if (!response.hasContent()) {
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject candidate = candidates.optJSONObject(0);
                if (candidate != null) {
                    JSONObject content = candidate.optJSONObject("content");
                    if (content != null) {
                        JSONArray parts = content.optJSONArray("parts");
                        if (parts != null) {
                            StringBuilder builder = new StringBuilder();
                            for (int i = 0; i < parts.length(); i++) {
                                JSONObject part = parts.optJSONObject(i);
                                if (part != null) {
                                    builder.append(part.optString("text", ""));
                                }
                            }
                            response.setContent(builder.toString());
                        }
                    }
                    String finish = candidate.optString("finishReason", "");
                    if (!finish.isEmpty()) {
                        response.setFinishReason(finish);
                    }
                }
            }
        }

        mergeUsage(root, response);

        if (!response.hasContent() && !response.hasToolCalls()) {
            String hint = response.structuralHint();
            String message = "Model returned an empty reply (" + hint + ")";
            if ("length".equals(response.getFinishReason()) && !response.getReasoning().isEmpty()) {
                message = "Model exhausted its output budget while reasoning, so it produced no "
                        + "visible answer (" + hint + "). Raise max output tokens or pick another model.";
            }
            throw new AiException(AiException.Kind.EMPTY_RESPONSE, 200, message,
                    response.getFinishReason());
        }

        return response;
    }

    /** Merges a usage block when the provider supplies one. */
    public static void mergeUsage(JSONObject root, ChatResponse response) {
        if (root == null || response == null) {
            return;
        }
        JSONObject usage = root.optJSONObject("usage");
        if (usage == null) {
            return;
        }
        int prompt = firstInt(usage, "prompt_tokens", "input_tokens", "promptTokenCount");
        int completion = firstInt(usage, "completion_tokens", "output_tokens", "candidatesTokenCount");
        if (prompt > 0) {
            response.setPromptTokens(prompt);
        }
        if (completion > 0) {
            response.setCompletionTokens(completion);
        }
    }

    private static int firstInt(JSONObject object, String... keys) {
        for (String key : keys) {
            int value = object.optInt(key, -1);
            if (value >= 0) {
                return value;
            }
        }
        return -1;
    }

    /** Extracts plain text from a string, a content-part array, or a text-bearing object. */
    public static String extractText(Object node) {
        if (node == null || node == JSONObject.NULL) {
            return "";
        }
        if (node instanceof String) {
            return (String) node;
        }
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                builder.append(extractText(array.opt(i)));
            }
            return builder.toString();
        }
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            for (String key : new String[]{"text", "output_text", "content", "value"}) {
                Object value = object.opt(key);
                if (value != null && value != JSONObject.NULL
                        && (value instanceof String || value instanceof JSONArray
                        || value instanceof JSONObject)) {
                    String text = extractText(value);
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }
            return "";
        }
        if (node instanceof Number || node instanceof Boolean) {
            return String.valueOf(node);
        }
        return "";
    }

    public static List<ToolCall> parseToolCalls(JSONArray array) {
        if (array == null || array.length() == 0) {
            return Collections.emptyList();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject function = item.optJSONObject("function");
            if (function == null) {
                function = item;
            }
            String name = function.optString("name", "");
            String arguments = extractArguments(function.opt("arguments"));
            if (!name.isEmpty()) {
                calls.add(new ToolCall(item.optString("id", "call_" + i), name, arguments));
            }
        }
        return calls;
    }

    private static String extractArguments(Object arguments) {
        if (arguments == null || arguments == JSONObject.NULL) {
            return "{}";
        }
        if (arguments instanceof String) {
            String value = ((String) arguments).trim();
            return value.isEmpty() ? "{}" : value;
        }
        return arguments.toString();
    }

    /** Reads the provider's error message from an error envelope. */
    public static String errorMessage(JSONObject root) {
        if (root == null) {
            return "";
        }
        Object error = root.opt("error");
        if (error instanceof String) {
            return ((String) error).trim();
        }
        if (error instanceof JSONObject) {
            JSONObject object = (JSONObject) error;
            String message = object.optString("message", "");
            if (message.isEmpty()) {
                message = object.optString("detail", "");
            }
            if (message.isEmpty()) {
                message = object.optString("error", "");
            }
            String type = object.optString("type", "");
            String code = object.optString("code", "");
            StringBuilder builder = new StringBuilder(message);
            if (!type.isEmpty()) {
                builder.append(builder.length() > 0 ? " [" : "[").append(type).append("]");
            }
            if (!code.isEmpty() && !code.equals(type)) {
                builder.append("(").append(code).append(")");
            }
            return builder.toString().trim();
        }
        String message = root.optString("message", "");
        if (message.isEmpty()) {
            message = root.optString("detail", "");
        }
        return message.trim();
    }

    /** Maps provider wording onto a classified failure. */
    public static AiException.Kind classify(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.US);
        if (text.contains("rate limit") || text.contains("rate_limit") || text.contains("quota")
                || text.contains("too many requests") || text.contains("overloaded")) {
            return AiException.Kind.RATE_LIMIT;
        }
        if (text.contains("invalid api key") || text.contains("invalid_api_key")
                || text.contains("unauthorized") || text.contains("api key")
                || text.contains("authentication") || text.contains("token is invalid")) {
            return AiException.Kind.AUTHENTICATION;
        }
        if (text.contains("forbidden") || text.contains("not permitted")
                || text.contains("not available in your") || text.contains("region")) {
            return AiException.Kind.FORBIDDEN;
        }
        if (text.contains("model") && (text.contains("not found") || text.contains("unknown"))) {
            return AiException.Kind.NOT_FOUND;
        }
        if (text.contains("timeout") || text.contains("timed out")) {
            return AiException.Kind.TIMEOUT;
        }
        if (text.contains("context length") || text.contains("too long")) {
            return AiException.Kind.BAD_REQUEST;
        }
        return AiException.Kind.UNKNOWN;
    }

    /** Parses the many shapes a provider may use for its model list. */
    public static List<String> parseModels(String body) throws AiException {
        if (body == null || body.trim().isEmpty()) {
            throw new AiException(AiException.Kind.PARSE, 200, "Model list response is empty");
        }
        String trimmed = body.trim();
        Set<String> ids = new LinkedHashSet<>();
        try {
            JSONObject root = new JSONObject(trimmed);
            Object error = root.opt("error");
            if (error != null && error != JSONObject.NULL) {
                String message = errorMessage(root);
                throw new AiException(classify(message), 0, message);
            }
            collectModels(root.optJSONArray("data"), ids);
            collectModels(root.optJSONArray("models"), ids);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                collectModels(data.optJSONArray("models"), ids);
            }
        } catch (JSONException e) {
            try {
                collectModels(new JSONArray(trimmed), ids);
            } catch (JSONException ignored) {
                throw new AiException(AiException.Kind.PARSE, 200,
                        "Unable to parse the model list returned by the provider");
            }
        }
        if (ids.isEmpty()) {
            throw new AiException(AiException.Kind.PARSE, 200,
                    "The provider returned no models");
        }
        List<String> list = new ArrayList<>(ids);
        Collections.sort(list);
        return list;
    }

    private static void collectModels(JSONArray array, Set<String> ids) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof String) {
                String value = ((String) item).trim();
                if (!value.isEmpty()) {
                    ids.add(value);
                }
            } else if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                String id = firstNonEmpty(
                        object.optString("id", ""),
                        object.optString("name", ""),
                        object.optString("model", ""),
                        object.optString("slug", ""));
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
    }

    public static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
