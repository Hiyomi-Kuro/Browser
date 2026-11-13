package com.kaori.browser.ai.chat;

/** One rendered entry in the AI panel. */
public final class ChatMessage {

    public enum Kind {
        USER,
        ASSISTANT,
        SYSTEM,
        ERROR,
        PAGE,
        STEP,
        TOOL,
        REPORT,
        CONFIRM
    }

    private final Kind kind;
    private final String text;
    private final String tag;
    private final long timestamp;

    public ChatMessage(Kind kind, String text) {
        this(kind, text, "");
    }

    public ChatMessage(Kind kind, String text, String tag) {
        this.kind = kind == null ? Kind.SYSTEM : kind;
        this.text = text == null ? "" : text;
        this.tag = tag == null ? "" : tag;
        this.timestamp = System.currentTimeMillis();
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public String getTag() {
        return tag;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isConversationTurn() {
        return kind == Kind.USER || kind == Kind.ASSISTANT;
    }

    public ChatMessage append(String delta) {
        return new ChatMessage(kind, text + (delta == null ? "" : delta), tag);
    }

    public ChatMessage withText(String newText) {
        return new ChatMessage(kind, newText, tag);
    }
}
