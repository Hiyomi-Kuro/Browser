package com.kaori.browser.ai.reader;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import com.kaori.browser.ai.AiExecutors;
import com.kaori.browser.export.MarkdownConverter;
import com.kaori.browser.reader.ArticleExtractor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the live page into memory and converts it to Markdown.
 *
 * <p>The page is read the same way the “save as Markdown” export reads it: waiting until
 * the document has finished loading, taking the complete DOM, and running the shared
 * {@link MarkdownConverter}. The Markdown is kept in full instead of being trimmed to a
 * prompt-sized excerpt, so the AI can read the whole page.</p>
 */
public final class PageContentExtractor {

    /** DOM size that is still converted in one pass. */
    private static final int MAX_HTML_CHARS = 3_000_000;
    /** Hard safety cap so a pathological page cannot exhaust the heap. */
    private static final int MAX_MARKDOWN_CHARS = 400_000;
    private static final int MAX_PLAIN_TEXT_CHARS = 400_000;
    private static final String MAX_PLAIN_TEXT_PLACEHOLDER = "__MAX_TEXT__";

    private static final long READY_POLL_MS = 300;
    private static final int MAX_READY_POLLS = 50;
    private static final int READY_STABLE_POLLS = 2;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onExtracted(PageSnapshot snapshot);

        void onError(String message);
    }

    private static final String READY_SCRIPT =
            "(function(){var b=document.body;"
                    + "var len=(b&&b.innerText)?b.innerText.length:0;"
                    + "return (document.readyState||'')+'|'+len;})()";

    private static final String STRUCTURE_SCRIPT =
            "(function(){" +
            "var anchors=document.querySelectorAll('a[href]');" +
            "var links=[];" +
            "for(var i=0;i<anchors.length&&links.length<300;i++){" +
            "var a=anchors[i];var h=a.href||'';if(!h||h.indexOf('javascript:')===0)continue;" +
            "var t=(a.innerText||a.textContent||'').replace(/\\s+/g,' ').trim();" +
            "links.push({text:(t||h).slice(0,160),href:h});" +
            "}" +
            "var tables=[];var ts=document.querySelectorAll('table');" +
            "for(var j=0;j<ts.length&&tables.length<40;j++){" +
            "var tb=ts[j];var rows=tb.rows?tb.rows.length:0;" +
            "var cols=(rows>0&&tb.rows[0])?tb.rows[0].cells.length:0;" +
            "var cap=(tb.caption&&tb.caption.innerText)||'';" +
            "tables.push({index:j,rows:rows,cols:cols,caption:cap.replace(/\\s+/g,' ').trim().slice(0,200)});" +
            "}" +
            "var meta={};" +
            "function attr(sel,name){" +
            "var e=null;try{e=document.querySelector(sel);}catch(x){return '';}" +
            "if(!e)return '';return e.getAttribute(name)||'';}" +
            "function put(k,v){v=(v||'').replace(/\\s+/g,' ').trim();if(v){meta[k]=v.slice(0,400);}}" +
            "put('description',attr('meta[name=\"description\"]','content'));" +
            "put('og_title',attr('meta[property=\"og:title\"]','content'));" +
            "put('og_site',attr('meta[property=\"og:site_name\"]','content'));" +
            "put('og_type',attr('meta[property=\"og:type\"]','content'));" +
            "put('author',attr('meta[name=\"author\"]','content'));" +
            "put('published',attr('meta[property=\"article:published_time\"]','content'));" +
            "put('modified',attr('meta[property=\"article:modified_time\"]','content'));" +
            "put('canonical',attr('link[rel=\"canonical\"]','href'));" +
            "put('language',(document.documentElement&&document.documentElement.lang)||'');" +
            "var text=document.body?(document.body.innerText||''):'';" +
            "var scrollHeight=document.documentElement?document.documentElement.scrollHeight:0;" +
            "return JSON.stringify({title:document.title||'',url:location.href," +
            "text:text.slice(0," + MAX_PLAIN_TEXT_CHARS + "),links:links,tables:tables,meta:meta," +
            "scrollY:window.scrollY||0,scrollHeight:scrollHeight,viewportHeight:window.innerHeight||0," +
            "forms:document.querySelectorAll('form').length,readyState:document.readyState});" +
            "})()";

    private PageContentExtractor() {
    }

    /**
     * Must be called on the Android main thread. Waits for the page to finish loading,
     * then extracts and converts off the UI thread.
     */
    public static void extract(WebView webView, Callback callback) {
        if (callback == null) {
            return;
        }
        if (webView == null) {
            callback.onError("No browser tab is open");
            return;
        }
        String url = webView.getUrl();
        if (url == null || url.isEmpty() || url.startsWith("about:")) {
            callback.onError("This tab has no readable page yet");
            return;
        }
        awaitLoaded(webView, 0, 0, callback);
    }

    /** Polls document.readyState until the document reports "complete" twice in a row. */
    private static void awaitLoaded(final WebView webView, final int polls,
                                    final int stable, final Callback callback) {
        if (polls >= MAX_READY_POLLS) {
            extractNow(webView, callback);
            return;
        }
        try {
            webView.evaluateJavascript(READY_SCRIPT, value -> {
                String state = unwrapString(value);
                boolean complete = state.startsWith("complete|");
                int nextStable = complete ? stable + 1 : 0;
                if (nextStable >= READY_STABLE_POLLS) {
                    extractNow(webView, callback);
                    return;
                }
                MAIN.postDelayed(() -> awaitLoaded(webView, polls + 1, nextStable, callback),
                        READY_POLL_MS);
            });
        } catch (RuntimeException e) {
            callback.onError("The page is no longer available");
        }
    }

    private static void extractNow(final WebView webView, final Callback callback) {
        ArticleExtractor.extractDocument(webView, new ArticleExtractor.DocumentCallback() {
            @Override
            public void onExtracted(ArticleExtractor.ExtractedDocument document) {
                try {
                    webView.evaluateJavascript(STRUCTURE_SCRIPT, value -> {
                        StructuredData structured;
                        try {
                            structured = parseStructured(value);
                        } catch (Exception e) {
                            structured = new StructuredData();
                        }
                        StructuredData finalStructured = structured;
                        AiExecutors.background().execute(
                                () -> build(document, finalStructured, callback));
                    });
                } catch (RuntimeException e) {
                    callback.onError("Unable to read this page");
                }
            }

            @Override
            public void onError() {
                callback.onError("Unable to read this page");
            }
        });
    }

    private static void build(ArticleExtractor.ExtractedDocument document,
                              StructuredData structured,
                              Callback callback) {
        String html = document.html;
        String plainText = structured.text;
        boolean htmlTooLarge = html.length() > MAX_HTML_CHARS;
        String markdown = "";

        if (!htmlTooLarge) {
            markdown = MarkdownConverter.toMarkdown(html);
        }
        if (markdown.isEmpty()) {
            markdown = plainText;
        }

        String title = document.title.isEmpty() ? structured.title : document.title;
        String url = document.url.isEmpty() ? structured.url : document.url;

        if (markdown.isEmpty()) {
            AiExecutors.main().execute(() -> callback.onError("This page has no readable text"));
            return;
        }

        int originalChars = markdown.length();
        boolean capped = originalChars > MAX_MARKDOWN_CHARS;
        if (capped) {
            markdown = markdown.substring(0, MAX_MARKDOWN_CHARS);
        }

        PageSnapshot snapshot = new PageSnapshot(
                title,
                url,
                markdown,
                plainText,
                structured.links,
                structured.tables,
                structured.metadata,
                System.currentTimeMillis(),
                htmlTooLarge || capped,
                originalChars);

        AiExecutors.main().execute(() -> callback.onExtracted(snapshot));
    }

    private static String unwrapString(String value) {
        if (value == null || "null".equals(value)) {
            return "";
        }
        try {
            Object parsed = new JSONTokener(value).nextValue();
            return parsed instanceof String ? (String) parsed : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static StructuredData parseStructured(String value) throws Exception {
        StructuredData data = new StructuredData();
        Object parsed = new JSONTokener(value).nextValue();
        if (!(parsed instanceof String)) {
            return data;
        }
        JSONObject root = new JSONObject((String) parsed);
        data.title = root.optString("title", "");
        data.url = root.optString("url", "");
        data.text = root.optString("text", "");

        JSONArray links = root.optJSONArray("links");
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.optJSONObject(i);
                if (link == null) {
                    continue;
                }
                String href = link.optString("href", "");
                if (href.isEmpty()) {
                    continue;
                }
                data.links.add(new PageSnapshot.Link(link.optString("text", ""), href));
            }
        }

        JSONArray tables = root.optJSONArray("tables");
        if (tables != null) {
            for (int i = 0; i < tables.length(); i++) {
                JSONObject table = tables.optJSONObject(i);
                if (table == null) {
                    continue;
                }
                data.tables.add(new PageSnapshot.TableRef(
                        table.optInt("index", i),
                        table.optInt("rows", 0),
                        table.optInt("cols", 0),
                        table.optString("caption", "")));
            }
        }

        JSONObject meta = root.optJSONObject("meta");
        if (meta != null) {
            java.util.Iterator<String> keys = meta.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String raw = meta.optString(key, "");
                if (!raw.isEmpty()) {
                    data.metadata.put(key, raw);
                }
            }
        }
        return data;
    }

    private static final class StructuredData {
        String title = "";
        String url = "";
        String text = "";
        final List<PageSnapshot.Link> links = new ArrayList<>();
        final List<PageSnapshot.TableRef> tables = new ArrayList<>();
        final Map<String, String> metadata = new LinkedHashMap<>();
    }
}
