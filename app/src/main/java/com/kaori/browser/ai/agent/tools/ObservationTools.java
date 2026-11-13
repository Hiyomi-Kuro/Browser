package com.kaori.browser.ai.agent.tools;

import com.kaori.browser.ai.agent.observation.BrowserObservation;
import com.kaori.browser.ai.agent.observation.ElementInfo;
import com.kaori.browser.ai.agent.observation.FormInfo;
import com.kaori.browser.ai.agent.observation.LinkInfo;
import com.kaori.browser.ai.reader.ContentBudget;
import com.kaori.browser.ai.reader.PageSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

/** browser.get_page_info / get_dom / get_text / get_links / get_forms / get_elements. */
public final class ObservationTools {

    private static final String DOM_OUTLINE_SCRIPT =
            "(function(){" +
            "var headings=[];var hs=document.querySelectorAll('h1,h2,h3,h4,h5,h6');" +
            "for(var i=0;i<hs.length&&headings.length<30;i++){" +
            "headings.push({level:parseInt((hs[i].tagName||'h1').slice(1),10)||1," +
            "text:(hs[i].innerText||'').replace(/\\s+/g,' ').trim().slice(0,160)});}" +
            "var paras=[];var ps=document.querySelectorAll('p');" +
            "for(var j=0;j<ps.length&&paras.length<40;j++){" +
            "var t=(ps[j].innerText||'').replace(/\\s+/g,' ').trim();" +
            "if(t.length>0)paras.push(t.slice(0,300));}" +
            "return JSON.stringify({headings:headings,paragraphs:paras," +
            "images:document.querySelectorAll('img[src]').length," +
            "lists:document.querySelectorAll('ul,ol').length," +
            "tables:document.querySelectorAll('table').length," +
            "forms:document.querySelectorAll('form').length," +
            "links:document.querySelectorAll('a[href]').length," +
            "textLength:(document.body?(document.body.innerText||'').length:0)});" +
            "})()";

    private ObservationTools() {
    }

    public static final class GetPageInfo implements BrowserTool {
        @Override
        public String name() {
            return "browser.get_page_info";
        }

        @Override
        public String description() {
            return "Read the current title, URL, loading state and scroll position.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties());
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            BrowserObservation observation = context.observeNow();
            if (observation == null) {
                return ToolResult.fail("Unable to read the current page state.");
            }
            StringBuilder builder = new StringBuilder();
            builder.append("Title: ").append(observation.title).append('\n');
            builder.append("URL: ").append(observation.url).append('\n');
            builder.append("Ready: ").append(observation.readyState).append('\n');
            builder.append("Scroll: ").append(observation.scrollY).append('/')
                    .append(observation.scrollHeight).append(" (viewport ")
                    .append(observation.viewportHeight).append(")\n");
            builder.append("Text length: ").append(observation.textLength).append('\n');
            builder.append("Interactive elements: ").append(observation.elements.size())
                    .append('\n');
            builder.append("Forms: ").append(observation.forms.size())
                    .append(", tables: ").append(observation.tables.size())
                    .append(", links: ").append(observation.links.size()).append('\n');
            if (observation.dialogOpen) {
                builder.append("A modal dialog is open.\n");
            }
            if (observation.iframeCount > 0) {
                builder.append(observation.iframeCount)
                        .append(" iframe(s) present; cross-origin frames are not readable.\n");
            }
            return ToolResult.ok("Page info read", builder.toString());
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return true;
        }
    }

    public static final class GetDom implements BrowserTool {
        @Override
        public String name() {
            return "browser.get_dom";
        }

        @Override
        public String description() {
            return "Read a filtered DOM outline: headings, paragraphs, counts of images, "
                    + "tables and forms.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "max_paragraphs", ToolSchemas.integerProperty("Paragraph limit", 1, 60)));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            JSONObject outline = JsBridge.evaluateJson(context.host, context.webView(),
                    DOM_OUTLINE_SCRIPT, context.toolTimeoutMs);
            if (outline == null) {
                return ToolResult.fail("Unable to read the DOM outline.");
            }
            int maxParagraphs = arguments.optInt("max_paragraphs", 25);
            StringBuilder builder = new StringBuilder();
            builder.append("Text length: ").append(outline.optInt("textLength", 0)).append('\n');
            builder.append("Images: ").append(outline.optInt("images", 0))
                    .append(", lists: ").append(outline.optInt("lists", 0))
                    .append(", tables: ").append(outline.optInt("tables", 0))
                    .append(", forms: ").append(outline.optInt("forms", 0))
                    .append(", links: ").append(outline.optInt("links", 0)).append('\n');

            JSONArray headings = outline.optJSONArray("headings");
            if (headings != null && headings.length() > 0) {
                builder.append("\nHeadings:\n");
                for (int i = 0; i < headings.length(); i++) {
                    JSONObject heading = headings.optJSONObject(i);
                    if (heading == null) {
                        continue;
                    }
                    builder.append("h").append(heading.optInt("level", 1)).append(' ')
                            .append(heading.optString("text", "")).append('\n');
                }
            }

            JSONArray paragraphs = outline.optJSONArray("paragraphs");
            if (paragraphs != null && paragraphs.length() > 0) {
                builder.append("\nParagraphs:\n");
                int limit = Math.min(paragraphs.length(), Math.max(1, maxParagraphs));
                for (int i = 0; i < limit; i++) {
                    builder.append("- ").append(paragraphs.optString(i, "")).append('\n');
                }
            }
            return ToolResult.ok("DOM outline read",
                    ContentBudget.trim(builder.toString(), 9000));
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return true;
        }
    }

    public static final class GetText implements BrowserTool {
        @Override
        public String name() {
            return "browser.get_text";
        }

        @Override
        public String description() {
            return "Read the main text of the page as Markdown, trimmed to a short excerpt.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "max_chars", ToolSchemas.integerProperty("Character limit", 500, 20000)));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PageReading.Result read = PageReading.read(context.host, context.toolTimeoutMs);
            if (!read.ok()) {
                return ToolResult.fail(read.error);
            }
            int maxChars = arguments.optInt("max_chars", 6000);
            PageSnapshot snapshot = read.snapshot;
            context.recordSource(snapshot.getTitle(), snapshot.getUrl(),
                    excerpt(snapshot.getMarkdown()), "text excerpt");
            String data = snapshot.toPromptBlock();
            return ToolResult.ok("Read " + snapshot.getCharCount() + " characters",
                    ContentBudget.trim(data, Math.max(500, Math.min(20000, maxChars))));
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return true;
        }
    }

    public static final class GetLinks implements BrowserTool {
        @Override
        public String name() {
            return "browser.get_links";
        }

        @Override
        public String description() {
            return "List the links on the current page with their targets.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "max_links", ToolSchemas.integerProperty("Link limit", 1, 100)));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            BrowserObservation observation = context.observeNow();
            if (observation == null) {
                return ToolResult.fail("Unable to read the current page.");
            }
            int limit = Math.min(arguments.optInt("max_links", 40), observation.links.size());
            StringBuilder builder = new StringBuilder();
            builder.append("Links (").append(observation.links.size()).append("):\n");
            for (int i = 0; i < limit; i++) {
                LinkInfo link = observation.links.get(i);
                builder.append(link.describe()).append('\n');
            }
            return ToolResult.ok("Listed " + limit + " links",
                    ContentBudget.trim(builder.toString(), 8000));
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return true;
        }
    }

    public static final class GetForms implements BrowserTool {
        @Override
        public String name() {
            return "browser.get_forms";
        }

        @Override
        public String description() {
            return "Describe the forms on the page and their fields.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties());
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            BrowserObservation observation = context.observeNow();
            if (observation == null) {
                return ToolResult.fail("Unable to read the current page.");
            }
            if (observation.forms.isEmpty()) {
                return ToolResult.ok("No forms on this page.");
            }
            StringBuilder builder = new StringBuilder();
            for (FormInfo form : observation.forms) {
                builder.append(form.describe()).append('\n');
            }
            return ToolResult.ok(observation.forms.size() + " form(s) found",
                    ContentBudget.trim(builder.toString(), 6000));
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return true;
        }
    }

    public static final class GetElements implements BrowserTool {
        @Override
        public String name() {
            return "browser.get_elements";
        }

        @Override
        public String description() {
            return "Take a fresh snapshot of the interactable elements. Element ids are "
                    + "only valid for this snapshot.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "max_elements", ToolSchemas.integerProperty("Element limit", 1, 220)));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            BrowserObservation observation = context.observeNow();
            if (observation == null) {
                return ToolResult.fail("Unable to snapshot the page elements.");
            }
            int limit = Math.min(arguments.optInt("max_elements", 80),
                    observation.elements.size());
            StringBuilder builder = new StringBuilder();
            builder.append("Snapshot generation ").append(observation.generation)
                    .append(". ").append(observation.elements.size())
                    .append(" interactable elements on ").append(observation.url).append('\n');
            for (int i = 0; i < limit; i++) {
                ElementInfo element = observation.elements.get(i);
                builder.append(element.describe()).append('\n');
            }
            return ToolResult.ok("Snapshot taken (" + observation.elements.size()
                    + " elements)", ContentBudget.trim(builder.toString(), 12000));
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return true;
        }
    }

    static String excerpt(String markdown) {
        if (markdown == null) {
            return "";
        }
        String single = markdown.replace('\n', ' ').trim();
        return single.length() <= 400 ? single : single.substring(0, 400);
    }
}
