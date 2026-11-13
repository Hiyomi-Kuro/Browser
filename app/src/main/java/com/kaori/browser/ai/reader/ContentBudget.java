package com.kaori.browser.ai.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure text-budget helpers: keeps model prompts bounded and provides segment plans
 * for very large pages without loading everything into one prompt.
 */
public final class ContentBudget {

    public static final int DEFAULT_MAX_CHARS = 24000;
    public static final int MIN_SEGMENT_CHARS = 2000;

    private static final String HEAD_CUT = "\n\n[... content omitted to stay within the reading budget ...]\n\n";

    private ContentBudget() {
    }

    /** Keeps the beginning and the end of a document within {@code maxChars}. */
    public static String trim(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        int headShare = (int) (maxChars * 0.7);
        int tailShare = maxChars - headShare - HEAD_CUT.length();
        if (tailShare <= 0) {
            return text.substring(0, Math.min(maxChars, text.length()));
        }
        String head = text.substring(0, Math.min(headShare, text.length()));
        String tail = text.substring(Math.max(0, text.length() - tailShare));
        return head + HEAD_CUT + tail;
    }

    public static boolean isTruncated(String original, int maxChars) {
        return original != null && maxChars > 0 && original.length() > maxChars;
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    /** Splits a document into ordered segments of at most {@code segmentChars}. */
    public static List<String> segments(String text, int segmentChars) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        int size = Math.max(MIN_SEGMENT_CHARS, segmentChars);
        List<String> result = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + size);
            if (end < text.length()) {
                int boundary = text.lastIndexOf('\n', end);
                if (boundary > index + size / 2) {
                    end = boundary;
                }
            }
            result.add(text.substring(index, end));
            index = end;
        }
        return result;
    }

    /** Joins a numbered segment list for prompt display. */
    public static String describeSegments(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return "1 segment";
        }
        return segments.size() + (segments.size() == 1 ? " segment" : " segments");
    }
}
