package com.kaori.browser.ai.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A simple column/row table with deterministic Markdown, CSV and JSON output.
 *
 * <p>Used to turn a model's comparison table into a checkable artefact. Missing values
 * are marked as "not stated"; nothing is auto-filled.</p>
 */
public final class StructuredTable {

    public static final String MISSING = "not stated";

    private final List<String> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    public StructuredTable(List<String> columns) {
        if (columns != null) {
            for (String column : columns) {
                if (column != null && !column.trim().isEmpty()) {
                    this.columns.add(column.trim());
                }
            }
        }
    }

    public List<String> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return columns.size();
    }

    public void addRow(List<String> values) {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String value = values != null && i < values.size() ? values.get(i) : "";
            row.add(value == null || value.trim().isEmpty() ? MISSING : value.trim());
        }
        rows.add(row);
    }

    /** Drops duplicate rows (all columns equal, case-insensitive). */
    public int dedupe() {
        Set<String> seen = new LinkedHashSet<>();
        List<List<String>> unique = new ArrayList<>();
        for (List<String> row : rows) {
            String key = String.join("|", row).toLowerCase(Locale.US);
            if (seen.add(key)) {
                unique.add(row);
            }
        }
        int removed = rows.size() - unique.size();
        rows.clear();
        rows.addAll(unique);
        return removed;
    }

    public List<String> columnsWithMissingValues() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            for (List<String> row : rows) {
                if (i < row.size() && MISSING.equals(row.get(i))) {
                    result.add(columns.get(i));
                    break;
                }
            }
        }
        return result;
    }

    public String toMarkdown() {
        if (columns.isEmpty()) {
            return "(no table)";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('|');
        for (String column : columns) {
            builder.append(' ').append(escape(column)).append(" |");
        }
        builder.append('\n').append('|');
        for (int i = 0; i < columns.size(); i++) {
            builder.append(" --- |");
        }
        for (List<String> row : rows) {
            builder.append('\n').append('|');
            for (int i = 0; i < columns.size(); i++) {
                builder.append(' ').append(escape(row.get(i))).append(" |");
            }
        }
        return builder.toString();
    }

    public String toCsv() {
        StringBuilder builder = new StringBuilder();
        builder.append(csvRow(columns)).append('\n');
        for (List<String> row : rows) {
            builder.append(csvRow(row)).append('\n');
        }
        return builder.toString();
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"columns\": [");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(quote(columns.get(i)));
        }
        builder.append("],\n  \"rows\": [");
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                builder.append(',');
            }
            builder.append("\n    {");
            List<String> row = rows.get(r);
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(quote(columns.get(i))).append(": ")
                        .append(quote(row.get(i)));
            }
            builder.append('}');
        }
        builder.append("\n  ]\n}\n");
        return builder.toString();
    }

    /**
     * Parses the first Markdown table in a model answer.
     *
     * @return the table, or {@code null} when the text contains no table.
     */
    public static StructuredTable parseMarkdownTable(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String[] lines = text.split("\n");
        List<String> header = null;
        List<String> separators = null;
        List<List<String>> body = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("|")) {
                if (header != null && !body.isEmpty()) {
                    break;
                }
                continue;
            }
            List<String> cells = splitRow(line);
            if (header == null) {
                header = cells;
                if (i + 1 < lines.length && lines[i + 1].trim().startsWith("|")) {
                    separators = splitRow(lines[i + 1].trim());
                    i++;
                }
                continue;
            }
            if (isSeparator(cells)) {
                continue;
            }
            body.add(cells);
        }

        if (header == null || header.isEmpty()) {
            return null;
        }
        if (separators != null && separators.size() != header.size()) {
            // Tolerate a malformed separator row; the header is still usable.
            separators = null;
        }
        StructuredTable table = new StructuredTable(header);
        for (List<String> row : body) {
            table.addRow(row);
        }
        return table;
    }

    private static boolean isSeparator(List<String> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            String value = cell.replace("-", "").replace(":", "").trim();
            if (!value.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitRow(String line) {
        String value = line.trim();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '|') {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private static String csvRow(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(csvCell(values.get(i)));
        }
        return builder.toString();
    }

    public static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\r", " ").replace("\n", " ").trim();
        if (text.contains(",") || text.contains("\"") || text.contains(";")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace('\n', ' ');
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
