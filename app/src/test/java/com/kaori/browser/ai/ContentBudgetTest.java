package com.kaori.browser.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kaori.browser.ai.reader.ContentBudget;

import org.junit.Test;

import java.util.List;

/** Prompt budgeting must bound input without silently dropping the whole page. */
public class ContentBudgetTest {

    @Test
    public void keepsShortTextIntact() {
        String text = "short page";
        assertEquals(text, ContentBudget.trim(text, 1000));
        assertFalse(ContentBudget.isTruncated(text, 1000));
    }

    @Test
    public void keepsHeadAndTailWhenTrimming() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            builder.append("line-").append(i).append('\n');
        }
        String text = builder.toString();
        String trimmed = ContentBudget.trim(text, 500);
        assertTrue(trimmed.length() <= 700);
        assertTrue(trimmed.startsWith("line-0"));
        assertTrue(trimmed.contains("content omitted"));
        assertTrue(trimmed.endsWith("\n"));
        assertTrue(ContentBudget.isTruncated(text, 500));
    }

    @Test
    public void splitsIntoSegments() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            builder.append("paragraph ").append(i).append('\n');
        }
        List<String> segments = ContentBudget.segments(builder.toString(), 2000);
        assertTrue(segments.size() > 1);
        StringBuilder rebuilt = new StringBuilder();
        for (String segment : segments) {
            rebuilt.append(segment);
        }
        assertEquals(builder.toString().length(), rebuilt.toString().length());
    }

    @Test
    public void emptyInputProducesEmptyOutput() {
        assertEquals("", ContentBudget.trim(null, 100));
        assertTrue(ContentBudget.segments("", 1000).isEmpty());
        assertEquals(0, ContentBudget.estimateTokens(""));
    }

    @Test
    public void estimatesTokens() {
        assertEquals(1, ContentBudget.estimateTokens("abc"));
        assertEquals(250, ContentBudget.estimateTokens(repeat('x', 1000)));
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
