package com.kaori.browser.reader;

import android.webkit.WebView;

import org.json.JSONTokener;

public final class ArticleExtractor {

    public interface Callback {
        void onExtracted(String html);
        void onError();
    }

    // Export the complete live DOM. Do not remove navigation, headers, footers,
    // sidebars, forms, scripts, styles, or other page sections here. The HTML-to-
    // Markdown converter decides how each HTML element is represented.
    private static final String EXTRACT_SCRIPT =
            "(function(){" +
            "var root=document.documentElement||document.body;" +
            "if(!root){return ''; }" +
            "var clone=root.cloneNode(true);" +
            "clone.querySelectorAll('a[href]').forEach(function(a){" +
            "try{a.setAttribute('href',new URL(a.getAttribute('href'),location.href).href);}catch(e){}" +
            "});" +
            "clone.querySelectorAll('[src]').forEach(function(el){" +
            "try{el.setAttribute('src',new URL(el.getAttribute('src'),location.href).href);}catch(e){}" +
            "});" +
            "return clone.outerHTML;" +
            "})()";

    private ArticleExtractor() {
    }

    public static void extract(WebView webView, Callback callback) {
        if (webView == null || callback == null || webView.getUrl() == null) {
            if (callback != null) {
                callback.onError();
            }
            return;
        }

        webView.evaluateJavascript(EXTRACT_SCRIPT, value -> {
            try {
                Object parsed = new JSONTokener(value).nextValue();
                if (!(parsed instanceof String)) {
                    callback.onError();
                    return;
                }

                String html = (String) parsed;
                if (html.trim().isEmpty()) {
                    callback.onError();
                    return;
                }

                callback.onExtracted(html);
            } catch (Exception e) {
                callback.onError();
            }
        });
    }
}
