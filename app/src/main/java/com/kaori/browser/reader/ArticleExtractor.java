package com.kaori.browser.reader;

import android.webkit.WebView;

import org.json.JSONTokener;

public final class ArticleExtractor {

    public interface Callback {
        void onExtracted(String html);
        void onError();
    }

    private static final String EXTRACT_SCRIPT =
            "(function(){" +
            "var selectors=[" +
            "'article'," +
            "'main'," +
            "'[role=\"main\"]'," +
            "'#main-content'," +
            "'#maincontent'," +
            "'.main-content'," +
            "'.article-page'," +
            "'.article-body'," +
            "'.article-content'," +
            "'.full-text'," +
            "'.fulltext'" +
            "];" +
            "var root=null;" +
            "var bestLength=0;" +
            "selectors.forEach(function(sel){" +
            "try{" +
            "document.querySelectorAll(sel).forEach(function(el){" +
            "var len=(el.innerText||'').trim().length;" +
            "if(len>bestLength){bestLength=len;root=el;}" +
            "});" +
            "}catch(e){}" +
            "});" +
            "if(!root||bestLength<300){root=document.body;}" +
            "var clone=root.cloneNode(true);" +
            "clone.querySelectorAll(" +
            "'script,style,noscript,nav,header,footer,aside,form,button,input,select,textarea,svg,canvas,iframe," +
            "[role=\"navigation\"],[aria-hidden=\"true\"]," +
            ".advertisement,.advertisements,.ad,.ads,.sidebar,.social-share,.share-buttons,.cookie-banner'" +
            ").forEach(function(n){n.remove();});" +
            "clone.querySelectorAll('a[href]').forEach(function(a){" +
            "try{a.setAttribute('href',new URL(a.getAttribute('href'),location.href).href);}catch(e){}" +
            "});" +
            "clone.querySelectorAll('img[src]').forEach(function(img){" +
            "try{img.setAttribute('src',new URL(img.getAttribute('src'),location.href).href);}catch(e){}" +
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
