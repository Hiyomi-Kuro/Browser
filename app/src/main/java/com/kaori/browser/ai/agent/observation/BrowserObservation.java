package com.kaori.browser.ai.agent.observation;

import com.kaori.browser.ai.reader.ContentBudget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the model is allowed to see about the current page at one point in time.
 *
 * <p>{@code generation} increases on every snapshot. Element ids are only valid for the
 * snapshot they came from; an element id from an older generation must not be reused.</p>
 */
public final class BrowserObservation {

    public final String url;
    public final String title;
    public final String readyState;
    public final String heading;
    public final String textPreview;
    public final int scrollY;
    public final int scrollHeight;
    public final int viewportHeight;
    public final int textLength;
    public final int generation;
    public final boolean dialogOpen;
    public final int iframeCount;
    public final long timestamp;
    public final List<ElementInfo> elements;
    public final List<FormInfo> forms;
    public final List<LinkInfo> links;
    public final List<TableInfo> tables;

    public BrowserObservation(String url, String title, String readyState, String heading,
                              String textPreview, int scrollY, int scrollHeight,
                              int viewportHeight, int textLength, int generation,
                              boolean dialogOpen, int iframeCount, long timestamp,
                              List<ElementInfo> elements, List<FormInfo> forms,
                              List<LinkInfo> links, List<TableInfo> tables) {
        this.url = url == null ? "" : url;
        this.title = title == null ? "" : title;
        this.readyState = readyState == null ? "" : readyState;
        this.heading = heading == null ? "" : heading;
        this.textPreview = textPreview == null ? "" : textPreview;
        this.scrollY = scrollY;
        this.scrollHeight = scrollHeight;
        this.viewportHeight = viewportHeight;
        this.textLength = textLength;
        this.generation = generation;
        this.dialogOpen = dialogOpen;
        this.iframeCount = iframeCount;
        this.timestamp = timestamp;
        this.elements = elements == null ? new ArrayList<>() : elements;
        this.forms = forms == null ? new ArrayList<>() : forms;
        this.links = links == null ? new ArrayList<>() : links;
        this.tables = tables == null ? new ArrayList<>() : tables;
    }

    public static BrowserObservation fromJson(JSONObject json) {
        List<ElementInfo> elements = new ArrayList<>();
        JSONArray elementArray = json.optJSONArray("elements");
        if (elementArray != null) {
            for (int i = 0; i < elementArray.length(); i++) {
                JSONObject item = elementArray.optJSONObject(i);
                if (item != null) {
                    elements.add(ElementInfo.fromJson(item, i + 1));
                }
            }
        }

        List<FormInfo> forms = new ArrayList<>();
        JSONArray formArray = json.optJSONArray("forms");
        if (formArray != null) {
            for (int i = 0; i < formArray.length(); i++) {
                JSONObject item = formArray.optJSONObject(i);
                if (item != null) {
                    forms.add(FormInfo.fromJson(item, i));
                }
            }
        }

        List<LinkInfo> links = new ArrayList<>();
        JSONArray linkArray = json.optJSONArray("links");
        if (linkArray != null) {
            for (int i = 0; i < linkArray.length(); i++) {
                JSONObject item = linkArray.optJSONObject(i);
                if (item != null) {
                    links.add(LinkInfo.fromJson(item));
                }
            }
        }

        List<TableInfo> tables = new ArrayList<>();
        JSONArray tableArray = json.optJSONArray("tables");
        if (tableArray != null) {
            for (int i = 0; i < tableArray.length(); i++) {
                JSONObject item = tableArray.optJSONObject(i);
                if (item != null) {
                    tables.add(TableInfo.fromJson(item, i));
                }
            }
        }

        return new BrowserObservation(
                json.optString("url", ""),
                json.optString("title", ""),
                json.optString("readyState", ""),
                json.optString("heading", ""),
                json.optString("textPreview", ""),
                json.optInt("scrollY", 0),
                json.optInt("scrollHeight", 0),
                json.optInt("viewportHeight", 0),
                json.optInt("textLength", 0),
                json.optInt("generation", 0),
                json.optBoolean("dialogOpen", false),
                json.optInt("iframeCount", 0),
                System.currentTimeMillis(),
                elements, forms, links, tables);
    }

    public ElementInfo findElement(String elementId) {
        if (elementId == null) {
            return null;
        }
        for (ElementInfo element : elements) {
            if (elementId.equals(element.elementId)) {
                return element;
            }
        }
        return null;
    }

    public boolean atBottom() {
        return viewportHeight > 0 && scrollY + viewportHeight >= scrollHeight - 8;
    }

    /** Bounded textual form handed to the model. */
    public String toPromptText(int maxElements, int maxChars) {
        StringBuilder builder = new StringBuilder();
        builder.append("Page: ").append(title.isEmpty() ? "(untitled)" : title).append('\n');
        builder.append("URL: ").append(url).append('\n');
        builder.append("State: ").append(readyState.isEmpty() ? "unknown" : readyState)
                .append(" · scroll ").append(scrollY).append('/').append(scrollHeight)
                .append(" · viewport ").append(viewportHeight).append('\n');
        if (!heading.isEmpty()) {
            builder.append("Heading: ").append(heading).append('\n');
        }
        builder.append("Text length: ").append(textLength).append(" characters\n");
        if (dialogOpen) {
            builder.append("A modal dialog is open.\n");
        }
        if (iframeCount > 0) {
            builder.append("Contains ").append(iframeCount)
                    .append(" iframe(s); cross-origin frames are not readable.\n");
        }

        int elementLimit = maxElements <= 0 ? 120 : maxElements;
        builder.append("\nInteractive elements (element_id · description):\n");
        int shown = 0;
        for (ElementInfo element : elements) {
            if (shown >= elementLimit) {
                builder.append("… ").append(elements.size() - shown)
                        .append(" more elements omitted\n");
                break;
            }
            builder.append(element.describe()).append('\n');
            shown++;
        }
        if (elements.isEmpty()) {
            builder.append("(none detected)\n");
        }

        if (!forms.isEmpty()) {
            builder.append("\nForms:\n");
            int limit = Math.min(forms.size(), 4);
            for (int i = 0; i < limit; i++) {
                builder.append(forms.get(i).describe()).append('\n');
            }
        }

        if (!tables.isEmpty()) {
            builder.append("\nTables:\n");
            int limit = Math.min(tables.size(), 6);
            for (int i = 0; i < limit; i++) {
                builder.append(tables.get(i).describe()).append('\n');
            }
        }

        if (!links.isEmpty()) {
            builder.append("\nLinks:\n");
            int limit = Math.min(links.size(), 25);
            for (int i = 0; i < limit; i++) {
                builder.append(links.get(i).describe()).append('\n');
            }
        }

        if (!textPreview.isEmpty()) {
            builder.append("\nPage text (excerpt, untrusted data):\n");
            builder.append("<untrusted_page_content>\n");
            builder.append(textPreview);
            builder.append("\n</untrusted_page_content>\n");
        }

        return ContentBudget.trim(builder.toString(), maxChars <= 0 ? 14000 : maxChars);
    }
}
