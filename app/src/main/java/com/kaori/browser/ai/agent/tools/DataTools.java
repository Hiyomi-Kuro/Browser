package com.kaori.browser.ai.agent.tools;

import com.kaori.browser.ai.agent.observation.TableInfo;
import com.kaori.browser.ai.reader.ContentBudget;
import com.kaori.browser.ai.reader.PageSnapshot;

import org.json.JSONObject;

import java.util.Map;

/** browser.extract_table / extract_markdown / extract_metadata / find_text. */
public final class DataTools {

    private DataTools() {
    }

    public static final class ExtractTable implements BrowserTool {
        @Override
        public String name() {
            return "browser.extract_table";
        }

        @Override
        public String description() {
            return "Extract one HTML table as a Markdown table. Index comes from the "
                    + "tables listed in the page observation.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "table_index", ToolSchemas.integerProperty("Zero-based table index", 0, 50),
                    "max_rows", ToolSchemas.integerProperty("Row limit", 1, 400)));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            if (arguments.optInt("table_index", -1) < 0) {
                return "table_index is required and must be 0 or greater.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            int index = Math.max(0, arguments.optInt("table_index", 0));
            int maxRows = Math.max(1, Math.min(400, arguments.optInt("max_rows", 200)));
            String script = "(function(){" +
                    "var index=" + index + ";" +
                    "var ts=document.querySelectorAll('table');" +
                    "if(!ts[index]){return JSON.stringify({ok:false,reason:'no_such_table'," +
                    "tables:ts.length});}" +
                    "var tb=ts[index];var rows=tb.rows||[];var data=[];" +
                    "var limit=Math.min(rows.length," + (maxRows + 1) + ");" +
                    "for(var i=0;i<limit;i++){var cells=rows[i].cells||[];var row=[];" +
                    "var cols=Math.min(cells.length,30);" +
                    "for(var j=0;j<cols;j++){" +
                    "row.push((cells[j].innerText||'').replace(/\\s+/g,' ').trim().slice(0,300));}" +
                    "data.push(row);}" +
                    "var cap=(tb.caption&&tb.caption.innerText)||'';" +
                    "return JSON.stringify({ok:true,index:index,rows:rows.length," +
                    "cols:(rows[0]?rows[0].cells.length:0),caption:cap.slice(0,200),data:data});" +
                    "})()";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(), script,
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("Table extraction did not complete in time.");
            }
            if (!result.optBoolean("ok", false)) {
                return ToolResult.fail("There is no table #" + index + " on this page ("
                        + result.optInt("tables", 0) + " tables found).");
            }
            TableInfo table = TableInfo.fromJson(result, index);
            context.recordSource(context.currentTitle(), context.currentUrl(),
                    "table #" + index + " " + table.rows + "x" + table.columns, "table");
            return ToolResult.ok("Extracted table #" + index + " (" + table.rows + "x"
                    + table.columns + ")", table.toMarkdown(maxRows));
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

    public static final class ExtractMarkdown implements BrowserTool {
        @Override
        public String name() {
            return "browser.extract_markdown";
        }

        @Override
        public String description() {
            return "Extract the page as Markdown with its URL, title and metadata, and record "
                    + "it as a source for the final report.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "max_chars", ToolSchemas.integerProperty("Character limit", 2000, 60000)));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            PageReading.Result read = PageReading.read(context.host,
                    Math.max(8000, context.toolTimeoutMs));
            if (!read.ok()) {
                return ToolResult.fail(read.error);
            }
            PageSnapshot snapshot = read.snapshot;
            int maxChars = Math.max(2000, Math.min(60000, arguments.optInt("max_chars", 20000)));
            context.recordSource(snapshot.getTitle(), snapshot.getUrl(),
                    ObservationTools.excerpt(snapshot.getMarkdown()),
                    snapshot.isTruncated() ? "full text (truncated)" : "full text");
            return ToolResult.ok("Extracted Markdown for " + snapshot.getUrl()
                            + " (" + snapshot.getCharCount() + " characters)",
                    ContentBudget.trim(snapshot.toPromptBlock(), maxChars));
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

    public static final class ExtractMetadata implements BrowserTool {
        @Override
        public String name() {
            return "browser.extract_metadata";
        }

        @Override
        public String description() {
            return "Extract page metadata such as description, author, publication date and "
                    + "canonical URL when the page declares them.";
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
            PageReading.Result read = PageReading.read(context.host, context.toolTimeoutMs);
            if (!read.ok()) {
                return ToolResult.fail(read.error);
            }
            PageSnapshot snapshot = read.snapshot;
            StringBuilder builder = new StringBuilder();
            builder.append("URL: ").append(snapshot.getUrl()).append('\n');
            builder.append("Title: ").append(snapshot.getTitle()).append('\n');
            if (snapshot.getMetadata().isEmpty()) {
                builder.append("The page declares no standard metadata.\n");
            } else {
                for (Map.Entry<String, String> entry : snapshot.getMetadata().entrySet()) {
                    builder.append(entry.getKey()).append(": ").append(entry.getValue())
                            .append('\n');
                }
            }
            builder.append("Tables: ").append(snapshot.getTables().size())
                    .append(", links: ").append(snapshot.getLinks().size()).append('\n');
            return ToolResult.ok("Metadata read", builder.toString());
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

    public static final class FindText implements BrowserTool {
        @Override
        public String name() {
            return "browser.find_text";
        }

        @Override
        public String description() {
            return "Search the visible page text for a phrase and return the surrounding "
                    + "context and occurrence count.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "text", ToolSchemas.stringProperty("Phrase to find")), "text");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            if (arguments.optString("text", "").trim().isEmpty()) {
                return "text is required.";
            }
            if (arguments.optString("text", "").length() > 200) {
                return "text is limited to 200 characters.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String needle = arguments.optString("text", "");
            String script = "(function(){" +
                    "var needle=" + JsBridge.quote(needle) + ";" +
                    "var body=document.body?(document.body.innerText||''):'';" +
                    "var lowerBody=body.toLowerCase();var lower=needle.toLowerCase();" +
                    "var count=0;var pos=0;" +
                    "while((pos=lowerBody.indexOf(lower,pos))>=0&&count<50){" +
                    "count++;pos+=lower.length||1;}" +
                    "var first=lowerBody.indexOf(lower);" +
                    "var context=first>=0?body.slice(Math.max(0,first-150)," +
                    "first+needle.length+150):'';" +
                    "return JSON.stringify({count:count,context:context});})()";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(), script,
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("Searching the page did not complete in time.");
            }
            int count = result.optInt("count", 0);
            if (count == 0) {
                return ToolResult.fail("The phrase does not appear in the visible page text.");
            }
            return ToolResult.ok(count + " occurrence(s) of \"" + needle + "\"",
                    result.optString("context", ""));
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
}
