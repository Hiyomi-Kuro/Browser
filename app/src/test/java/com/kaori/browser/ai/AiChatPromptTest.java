package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.chat.AiChatPrompt;
import com.kaori.browser.ai.chat.ChatMessage;
import com.kaori.browser.ai.model.AiMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Prompt construction and bounded history. */
public class AiChatPromptTest {

    @Test
    public void systemPromptStatesTheRules() {
        String prompt = AiChatPrompt.systemPrompt("Simplified Chinese");
        assertTrue(prompt.contains("Simplified Chinese"));
        assertTrue(prompt.contains("untrusted_page_content"));
        assertTrue(prompt.contains("Never present a guess"));
    }

    @Test
    public void historyKeepsOrderAndDropsNonTurns() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessage.Kind.USER, "first"));
        messages.add(new ChatMessage(ChatMessage.Kind.STEP, "step detail"));
        messages.add(new ChatMessage(ChatMessage.Kind.ASSISTANT, "second"));
        messages.add(new ChatMessage(ChatMessage.Kind.ERROR, "boom"));
        messages.add(new ChatMessage(ChatMessage.Kind.USER, "third"));

        List<AiMessage> history = AiChatPrompt.history(messages, 10, 5000);
        assertEquals(3, history.size());
        assertEquals("first", history.get(0).getContent());
        assertEquals(AiMessage.ROLE_USER, history.get(0).getRole());
        assertEquals(AiMessage.ROLE_ASSISTANT, history.get(1).getRole());
        assertEquals("third", history.get(2).getContent());
    }

    @Test
    public void historyRespectsMessageLimit() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(new ChatMessage(i % 2 == 0 ? ChatMessage.Kind.USER
                    : ChatMessage.Kind.ASSISTANT, "m" + i));
        }
        List<AiMessage> history = AiChatPrompt.history(messages, 4, 10000);
        assertEquals(4, history.size());
        assertEquals("m16", history.get(0).getContent());
    }

    @Test
    public void historyTrimsLongMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            builder.append('x');
        }
        messages.add(new ChatMessage(ChatMessage.Kind.USER, builder.toString()));
        List<AiMessage> history = AiChatPrompt.history(messages, 2, 100);
        assertEquals(1, history.size());
        assertTrue(history.get(0).getContent().length() <= 101);
    }

    @Test
    public void instructionsMentionTheRequestedMode() {
        assertTrue(AiChatPrompt.summaryInstruction(true).contains("Markdown headings"));
        assertTrue(AiChatPrompt.summaryInstruction(false).contains("five short bullet"));
        assertTrue(AiChatPrompt.translateInstruction("English").contains("English"));
        assertTrue(AiChatPrompt.explainInstruction("term").contains("term"));
        assertTrue(AiChatPrompt.extractInstruction("").contains("Markdown table"));
    }
}
