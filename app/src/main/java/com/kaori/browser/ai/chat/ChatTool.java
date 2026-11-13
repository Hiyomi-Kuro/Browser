package com.kaori.browser.ai.chat;

import org.json.JSONObject;

/**
 * One user-configurable button in the AI panel's tool list.
 *
 * <p>Supported placeholders inside {@link #getPrompt()}: {@code {选中内容}} (the text the
 * user selected on the page) and {@code {语言}} (the translation target language).</p>
 */
public final class ChatTool {

    public static final String KIND_PROMPT = "prompt";
    public static final String KIND_READ_PAGE = "read_page";

    public static final String PLACEHOLDER_SELECTION = "{选中内容}";
    public static final String PLACEHOLDER_LANGUAGE = "{语言}";

    private final String id;
    private final String name;
    private final String prompt;
    private final String kind;
    private final boolean builtin;

    public ChatTool(String id, String name, String prompt, String kind, boolean builtin) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name.trim();
        this.prompt = prompt == null ? "" : prompt;
        this.kind = kind == null || kind.isEmpty() ? KIND_PROMPT : kind;
        this.builtin = builtin;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getKind() {
        return kind;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public boolean isReadPage() {
        return KIND_READ_PAGE.equals(kind);
    }

    /** True when the prompt wants the current selection injected. */
    public boolean usesSelection() {
        return prompt.contains(PLACEHOLDER_SELECTION);
    }

    public boolean hasPrompt() {
        return !prompt.trim().isEmpty();
    }

    /** The prompt with placeholders resolved; {@code language} may be null. */
    public String resolvePrompt(String selection, String language) {
        String resolved = prompt;
        if (usesSelection()) {
            resolved = resolved.replace(PLACEHOLDER_SELECTION, selection == null ? "" : selection);
        }
        if (language != null && !language.isEmpty()) {
            resolved = resolved.replace(PLACEHOLDER_LANGUAGE, language);
        }
        return resolved;
    }

    public ChatTool withNameAndPrompt(String newName, String newPrompt) {
        return new ChatTool(id, newName, newPrompt, kind, builtin);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("name", name);
            object.put("prompt", prompt);
            object.put("kind", kind);
            object.put("builtin", builtin);
        } catch (Exception ignored) {
        }
        return object;
    }

    public static ChatTool fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        String name = object.optString("name", "").trim();
        if (name.isEmpty()) {
            return null;
        }
        return new ChatTool(
                object.optString("id", ""),
                name,
                object.optString("prompt", ""),
                object.optString("kind", KIND_PROMPT),
                object.optBoolean("builtin", false));
    }

    public String describe() {
        if (!hasPrompt()) {
            return name;
        }
        String single = prompt.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return name + " · " + (single.length() <= 60 ? single : single.substring(0, 60) + "…");
    }
}
