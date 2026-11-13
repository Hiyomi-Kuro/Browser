package com.kaori.browser.reader;

import android.webkit.WebView;

import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Extracts the live DOM of the current page as HTML.
 *
 * <p>The complete DOM is cloned, not trimmed: navigation, headers, footers, sidebars
 * and forms stay in place and the HTML-to-Markdown converter decides how each element
 * is represented. {@link DocumentCallback} additionally returns the page title and URL
 * so callers do not need extra JavaScript round trips.</p>
 */
public final class ArticleExtractor {

    public interface Callback {
        void onExtracted(String html);

        void onError();
    }

    public interface DocumentCallback {
        void onExtracted(ExtractedDocument document);

        void onError();
    }

    /** Result of a DOM extraction: resolved HTML plus the page identity. */
    public static final class ExtractedDocument {
        public final String html;
        public final String title;
        public final String url;

        public ExtractedDocument(String html, String title, String url) {
            this.html = html == null ? "" : html;
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
        }
    }

    private static final String EXTRACT_SCRIPT =
            "(function(){" +
            "var root=document.documentElement||document.body;" +
            "if(!root){return JSON.stringify({html:'',title:'',url:''});}" +
            "var clone=root.cloneNode(true);" +
            "clone.querySelectorAll('a[href]').forEach(function(a){" +
            "try{a.setAttribute('href',new URL(a.getAttribute('href'),location.href).href);}catch(e){}" +
            "});" +
            "clone.querySelectorAll('[src]').forEach(function(el){" +
            "try{el.setAttribute('src',new URL(el.getAttribute('src'),location.href).href);}catch(e){}" +
            "});" +
            "return JSON.stringify({html:clone.outerHTML,title:document.title||'',url:location.href});" +
            "})()";

    private ArticleExtractor() {
    }

    public static void extract(WebView webView, Callback callback) {
        if (callback == null) {
            return;
        }
        extractDocument(webView, new DocumentCallback() {
            @Override
            public void onExtracted(ExtractedDocument document) {
                if (document.html.trim().isEmpty()) {
                    callback.onError();
                } else {
                    callback.onExtracted(document.html);
                }
            }

            @Override
            public void onError() {
                callback.onError();
            }
        });
    }

    public static void extractDocument(WebView webView, DocumentCallback callback) {
        if (callback == null) {
            return;
        }
        if (webView == null || webView.getUrl() == null) {
            callback.onError();
            return;
        }

        webView.evaluateJavascript(EXTRACT_SCRIPT, value -> {
            try {
                Object parsed = new JSONTokener(value).nextValue();
                if (!(parsed instanceof String)) {
                    callback.onError();
                    return;
                }
                JSONObject root = new JSONObject((String) parsed);
                String html = root.optString("html", "");
                if (html.trim().isEmpty()) {
                    callback.onError();
                    return;
                }
                callback.onExtracted(new ExtractedDocument(
                        html,
                        root.optString("title", ""),
                        root.optString("url", "")));
            } catch (Exception e) {
                callback.onError();
            }
        });
    }
}
