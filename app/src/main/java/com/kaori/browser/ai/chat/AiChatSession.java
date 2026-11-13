package com.kaori.browser.ai.chat;

import com.kaori.browser.ai.model.SessionIds;
import com.kaori.browser.ai.reader.PageSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chat state for one AI conversation.
 *
 * <p>The session id is created once and reused for every request so the provider can
 * keep routing/prompt caching stable ({@code x-opencode-session}). Starting a new
 * conversation produces a new id; sending a message never does.</p>
 */
public final class AiChatSession {

    private String id;
    private String title = "";
    private final long createdAt;
    private final List<ChatMessage> messages = new ArrayList<>();
    private PageSnapshot page;
    private int requestCount;
    private int promptTokens;
    private int completionTokens;

    public AiChatSession() {
        this.id = SessionIds.newSessionId();
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void add(ChatMessage message) {
        if (message != null) {
            messages.add(message);
            if (messages.size() > 200) {
                messages.remove(0);
            }
        }
    }

    public boolean replace(ChatMessage target, ChatMessage replacement) {
        int index = messages.indexOf(target);
        if (index < 0) {
            return false;
        }
        messages.set(index, replacement);
        return true;
    }

    /** Clears the conversation and starts a new provider-visible session. */
    public void reset() {
        messages.clear();
        page = null;
        title = "";
        requestCount = 0;
        promptTokens = 0;
        completionTokens = 0;
        id = SessionIds.newSessionId();
    }

    public PageSnapshot getPage() {
        return page;
    }

    public void setPage(PageSnapshot page) {
        this.page = page;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void recordUsage(int prompt, int completion) {
        requestCount++;
        promptTokens += Math.max(0, prompt);
        completionTokens += Math.max(0, completion);
    }

    public String lastAssistantText() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message.getKind() == ChatMessage.Kind.ASSISTANT) {
                return message.getText();
            }
        }
        return "";
    }
}
