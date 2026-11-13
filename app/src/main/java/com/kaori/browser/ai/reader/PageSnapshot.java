package com.kaori.browser.ai.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A page read fully in memory: no Markdown file is written just to feed the model.
 */
public final class PageSnapshot {

    private final String title;
    private final String url;
    private final String markdown;
    private final String plainText;
    private final List<Link> links;
    private final List<TableRef> tables;
    private final Map<String, String> metadata;
    private final long extractedAt;
    private final boolean truncated;
    private final int originalChars;

    public PageSnapshot(String title, String url, String markdown, String plainText,
                        List<Link> links, List<TableRef> tables, Map<String, String> metadata,
                        long extractedAt, boolean truncated, int originalChars) {
        this.title = title == null ? "" : title;
        this.url = url == null ? "" : url;
        this.markdown = markdown == null ? "" : markdown;
        this.plainText = plainText == null ? "" : plainText;
        this.links = links == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(links));
        this.tables = tables == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(tables));
        this.metadata = metadata == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.extractedAt = extractedAt;
        this.truncated = truncated;
        this.originalChars = originalChars;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getPlainText() {
        return plainText;
    }

    public List<Link> getLinks() {
        return links;
    }

    public List<TableRef> getTables() {
        return tables;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public long getExtractedAt() {
        return extractedAt;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public int getOriginalChars() {
        return originalChars;
    }

    public int getCharCount() {
        return markdown.length();
    }

    /** Builds the block sent to the model, wrapped so page text stays data. */
    public String toPromptBlock() {
        StringBuilder builder = new StringBuilder();
        builder.append("Title: ").append(title.isEmpty() ? "(untitled)" : title).append('\n');
        builder.append("URL: ").append(url).append('\n');
        if (!metadata.isEmpty()) {
            builder.append("Metadata:");
            int count = 0;
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (count >= 8) {
                    break;
                }
                builder.append(' ').append(entry.getKey()).append('=').append(entry.getValue())
                        .append(';');
                count++;
            }
            builder.append('\n');
        }
        if (truncated) {
            builder.append("Note: the extracted content was truncated from ")
                    .append(originalChars).append(" to ").append(markdown.length())
                    .append(" characters.\n");
        }
        if (!tables.isEmpty()) {
            builder.append("Tables: ").append(tables.size()).append('\n');
        }
        if (!links.isEmpty()) {
            builder.append("Links: ").append(links.size()).append('\n');
        }
        builder.append("\n<untrusted_page_content>\n");
        builder.append(markdown);
        builder.append("\n</untrusted_page_content>\n");
        return builder.toString();
    }

    /** Short human-readable description used by the chat panel. */
    public String describe() {
        return (title.isEmpty() ? url : title) + " · " + markdown.length() + " chars";
    }

    public static final class Link {
        public final String text;
        public final String href;

        public Link(String text, String href) {
            this.text = text == null ? "" : text;
            this.href = href == null ? "" : href;
        }
    }

    public static final class TableRef {
        public final int index;
        public final int rows;
        public final int columns;
        public final String caption;

        public TableRef(int index, int rows, int columns, String caption) {
            this.index = index;
            this.rows = rows;
            this.columns = columns;
            this.caption = caption == null ? "" : caption;
        }

        public String describe() {
            return "table #" + index + " (" + rows + "x" + columns + ")"
                    + (caption.isEmpty() ? "" : " " + caption);
        }
    }
}
