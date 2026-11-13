package com.kaori.browser.reader;

import android.content.Context;
import android.webkit.WebView;
import android.widget.Toast;

import com.kaori.browser.R;

import org.json.JSONTokener;

/**
 * Applies a reversible reader presentation to the current live page.
 *
 * <p>The implementation keeps the original DOM nodes in place. It marks and hides
 * sibling branches around the best article candidate and injects only a small style
 * sheet. Disabling reader mode removes those markers and restores the original
 * scroll position, so page history and JavaScript event handlers remain intact.</p>
 */
public final class ReaderModeManager {

    private static final String RESULT_ENABLED = "enabled";
    private static final String RESULT_DISABLED = "disabled";

    private static final String CHECK_SCRIPT =
            "(function(){" +
            "var root=document.documentElement;" +
            "return !!(root&&root.hasAttribute('data-kaori-reader-mode'));" +
            "})()";

    private static final String ENABLE_SCRIPT =
            "(function(){" +
            "var d=document,html=d.documentElement,body=d.body;" +
            "if(!html||!body){return 'unavailable';}" +
            "if(html.hasAttribute('data-kaori-reader-mode')){return 'enabled';}" +
            "var textLen=function(el){return ((el&&el.innerText)||'').replace(/\\s+/g,' ').trim().length;};" +
            "var score=function(el){" +
            "if(!el){return -1;}" +
            "var len=textLen(el);if(len<240){return -1;}" +
            "var p=el.querySelectorAll('p').length;" +
            "var media=el.querySelectorAll('img,figure,pre,blockquote,table').length;" +
            "var linkText=0;el.querySelectorAll('a').forEach(function(a){linkText+=textLen(a);});" +
            "var bonus=0,tag=(el.tagName||'').toLowerCase();" +
            "if(tag==='article'){bonus+=900;}else if(tag==='main'){bonus+=650;}" +
            "if(el.getAttribute('role')==='main'){bonus+=650;}" +
            "var hint=((el.id||'')+' '+(typeof el.className==='string'?el.className:'')).toLowerCase();" +
            "if(/article|post|entry|content|story|main/.test(hint)){bonus+=350;}" +
            "if(/comment|footer|header|nav|sidebar|related|share|social|advert|promo/.test(hint)){bonus-=900;}" +
            "return len+(p*140)+(media*90)+bonus-(linkText*0.7);" +
            "};" +
            "var best=null,bestScore=-1;" +
            "var primary=d.querySelectorAll('article,main,[role=main],.article,.article-body,.article-content,.post,.post-content,.entry-content,.story-body');" +
            "primary.forEach(function(el){var s=score(el);if(s>bestScore){bestScore=s;best=el;}});" +
            "if(!best||bestScore<700){" +
            "var fallback=body.querySelectorAll('section,div');" +
            "var limit=Math.min(fallback.length,1800);" +
            "for(var i=0;i<limit;i++){var el=fallback[i];if(el.querySelectorAll('p').length<2){continue;}var s=score(el);if(s>bestScore){bestScore=s;best=el;}}" +
            "}" +
            "if(!best||bestScore<700){return 'unavailable';}" +
            "html.setAttribute('data-kaori-reader-mode','1');" +
            "html.setAttribute('data-kaori-reader-scroll-y',String(window.scrollY||0));" +
            "best.setAttribute('data-kaori-reader-content','1');" +
            "var node=best;" +
            "while(node&&node!==body){" +
            "var parent=node.parentElement;if(!parent){break;}" +
            "parent.setAttribute('data-kaori-reader-ancestor','1');" +
            "Array.prototype.forEach.call(parent.children,function(child){if(child!==node){child.setAttribute('data-kaori-reader-hidden','1');}});" +
            "node=parent;" +
            "}" +
            "best.querySelectorAll('nav,aside,form,button,input,select,textarea,noscript').forEach(function(el){el.setAttribute('data-kaori-reader-hidden','1');});" +
            "var style=d.createElement('style');style.id='kaori-reader-style';" +
            "style.textContent=" +
            "'html[data-kaori-reader-mode]{background:#f6f3eb!important;}'+" +
            "'html[data-kaori-reader-mode] body{margin:0!important;padding:0!important;background:#f6f3eb!important;overflow-x:hidden!important;}'+" +
            "'[data-kaori-reader-hidden]{display:none!important;}'+" +
            "'[data-kaori-reader-ancestor]{display:block!important;position:static!important;float:none!important;width:auto!important;max-width:none!important;height:auto!important;min-height:0!important;margin:0!important;padding:0!important;transform:none!important;background:transparent!important;}'+" +
            "'[data-kaori-reader-content]{box-sizing:border-box!important;display:block!important;position:relative!important;float:none!important;width:auto!important;max-width:780px!important;height:auto!important;margin:0 auto!important;padding:40px 22px 72px!important;background:transparent!important;color:#202124!important;font-family:Georgia,serif!important;font-size:18px!important;line-height:1.75!important;letter-spacing:.01em!important;}'+" +
            "'[data-kaori-reader-content] *{max-width:100%!important;box-sizing:border-box!important;}'+" +
            "'[data-kaori-reader-content] p,[data-kaori-reader-content] li{font-size:1em!important;line-height:1.75!important;}'+" +
            "'[data-kaori-reader-content] h1,[data-kaori-reader-content] h2,[data-kaori-reader-content] h3{line-height:1.3!important;margin-top:1.5em!important;}'+" +
            "'[data-kaori-reader-content] img,[data-kaori-reader-content] video{height:auto!important;max-width:100%!important;}'+" +
            "'[data-kaori-reader-content] pre,[data-kaori-reader-content] table{overflow:auto!important;max-width:100%!important;}'+" +
            "'[data-kaori-reader-content] a{word-break:break-word!important;}'+" +
            "'@media (prefers-color-scheme:dark){html[data-kaori-reader-mode],html[data-kaori-reader-mode] body{background:#171717!important;}[data-kaori-reader-content]{color:#e8e6e3!important;}[data-kaori-reader-content] a{color:#8ab4f8!important;}}';" +
            "(d.head||html).appendChild(style);" +
            "best.scrollIntoView({block:'start'});" +
            "return 'enabled';" +
            "})()";

    private static final String DISABLE_SCRIPT =
            "(function(){" +
            "var d=document,html=d.documentElement;if(!html){return 'unavailable';}" +
            "var y=parseInt(html.getAttribute('data-kaori-reader-scroll-y')||'0',10);" +
            "var style=d.getElementById('kaori-reader-style');if(style){style.remove();}" +
            "d.querySelectorAll('[data-kaori-reader-hidden]').forEach(function(el){el.removeAttribute('data-kaori-reader-hidden');});" +
            "d.querySelectorAll('[data-kaori-reader-ancestor]').forEach(function(el){el.removeAttribute('data-kaori-reader-ancestor');});" +
            "d.querySelectorAll('[data-kaori-reader-content]').forEach(function(el){el.removeAttribute('data-kaori-reader-content');});" +
            "html.removeAttribute('data-kaori-reader-mode');" +
            "html.removeAttribute('data-kaori-reader-scroll-y');" +
            "window.scrollTo(0,isNaN(y)?0:y);" +
            "return 'disabled';" +
            "})()";

    private final Context context;

    public ReaderModeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void toggle(WebView webView) {
        if (webView == null || webView.getUrl() == null || webView.getUrl().startsWith("about:")) {
            showUnavailable();
            return;
        }

        webView.evaluateJavascript(CHECK_SCRIPT, value -> {
            if ("true".equals(value)) {
                disable(webView);
            } else {
                enable(webView);
            }
        });
    }

    private void enable(WebView webView) {
        webView.evaluateJavascript(ENABLE_SCRIPT, value -> {
            String result = parseResult(value);
            if (RESULT_ENABLED.equals(result)) {
                Toast.makeText(context, R.string.reader_mode_enabled, Toast.LENGTH_SHORT).show();
            } else {
                showUnavailable();
            }
        });
    }

    private void disable(WebView webView) {
        webView.evaluateJavascript(DISABLE_SCRIPT, value -> {
            String result = parseResult(value);
            if (RESULT_DISABLED.equals(result)) {
                Toast.makeText(context, R.string.reader_mode_disabled, Toast.LENGTH_SHORT).show();
            } else {
                showUnavailable();
            }
        });
    }

    private void showUnavailable() {
        Toast.makeText(context, R.string.reader_mode_unavailable, Toast.LENGTH_SHORT).show();
    }

    private static String parseResult(String value) {
        try {
            Object parsed = new JSONTokener(value).nextValue();
            return parsed instanceof String ? (String) parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
