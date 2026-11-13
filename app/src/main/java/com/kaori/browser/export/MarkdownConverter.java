package com.kaori.browser.export;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;

/**
 * The single HTML-to-Markdown conversion used by both the manual “save as Markdown”
 * export and the AI page reader, so the model sees exactly what the exporter would save.
 */
public final class MarkdownConverter {

    private MarkdownConverter() {
    }

    public static String toMarkdown(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        try {
            return FlexmarkHtmlConverter.builder().build().convert(html).trim();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
