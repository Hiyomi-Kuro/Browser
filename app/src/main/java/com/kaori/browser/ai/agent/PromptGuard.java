package com.kaori.browser.ai.agent;

import java.util.Locale;

/**
 * Prompt-injection hygiene.
 *
 * <p>Page text is always wrapped in explicit data delimiters, and obvious
 * instruction-shaped content is flagged in the log so the user can see why an action
 * was proposed. Page content can never change the tool set, the permissions or the
 * credentials.</p>
 */
public final class PromptGuard {

    private static final String[] SUSPICIOUS = {
            "ignore previous", "ignore all previous", "ignore the above", "disregard previous",
            "system prompt", "you are now", "new instructions", "reveal your",
            "print your instructions", "api key", "access token", "cookie", "password",
            "忽略之前", "忽略以上", "忽略前面", "系统提示", "你的提示词", "新的指令",
            "输出你的", "密钥", "密码"
    };

    private PromptGuard() {
    }

    public static String wrapUntrusted(String content) {
        String body = content == null ? "" : content;
        return "<untrusted_page_content>\n" + body + "\n</untrusted_page_content>";
    }

    public static boolean looksLikeInstruction(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.US);
        for (String marker : SUSPICIOUS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** Short, single-line excerpt safe for logs. */
    public static String sanitizeForLog(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String single = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        int limit = maxChars <= 0 ? 160 : maxChars;
        return single.length() <= limit ? single : single.substring(0, limit) + "…";
    }
}
