package com.kaori.browser.ai.agent.observation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One HTML table, optionally with its parsed rows. */
public final class TableInfo {

    public final int index;
    public final int rows;
    public final int columns;
    public final String caption;
    public final List<List<String>> data;

    public TableInfo(int index, int rows, int columns, String caption, List<List<String>> data) {
        this.index = index;
        this.rows = rows;
        this.columns = columns;
        this.caption = caption == null ? "" : caption;
        this.data = data == null ? new ArrayList<>() : data;
    }

    public static TableInfo fromJson(JSONObject json, int fallbackIndex) {
        List<List<String>> data = new ArrayList<>();
        JSONArray rows = json.optJSONArray("data");
        if (rows != null) {
            for (int i = 0; i < rows.length(); i++) {
                JSONArray row = rows.optJSONArray(i);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int j = 0; j < row.length(); j++) {
                        cells.add(row.optString(j, ""));
                    }
                }
                data.add(cells);
            }
        }
        return new TableInfo(json.optInt("index", fallbackIndex),
                json.optInt("rows", data.size()),
                json.optInt("cols", data.isEmpty() ? 0 : data.get(0).size()),
                json.optString("caption", ""),
                data);
    }

    public String describe() {
        return "table #" + index + " " + rows + "x" + columns
                + (caption.isEmpty() ? "" : " \"" + caption + "\"");
    }

    public String toMarkdown(int maxRows) {
        if (data.isEmpty()) {
            return describe() + " (no rows available)";
        }
        StringBuilder builder = new StringBuilder();
        List<String> header = data.get(0);
        builder.append('|');
        for (String cell : header) {
            builder.append(' ').append(escape(cell)).append(" |");
        }
        builder.append('\n').append('|');
        for (int i = 0; i < header.size(); i++) {
            builder.append(" --- |");
        }
        int limit = maxRows <= 0 ? data.size() : Math.min(data.size(), maxRows + 1);
        for (int i = 1; i < limit; i++) {
            builder.append('\n').append('|');
            List<String> row = data.get(i);
            for (int j = 0; j < header.size(); j++) {
                String cell = j < row.size() ? row.get(j) : "";
                builder.append(' ').append(escape(cell)).append(" |");
            }
        }
        return builder.toString();
    }

    public String toCsv() {
        StringBuilder builder = new StringBuilder();
        for (List<String> row : data) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(csvCell(row.get(i)));
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private static String escape(String cell) {
        if (cell == null) {
            return "";
        }
        return cell.replace("|", "\\|").replace('\n', ' ').trim();
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
}
