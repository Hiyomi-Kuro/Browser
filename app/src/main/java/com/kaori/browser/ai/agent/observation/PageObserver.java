package com.kaori.browser.ai.agent.observation;

import android.webkit.WebView;

import com.kaori.browser.ai.agent.BrowserHost;
import com.kaori.browser.ai.agent.tools.JsBridge;

import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Produces a page snapshot for the model.
 *
 * <p>Every snapshot tags the interactable elements it reports with a
 * {@code data-kaori-ai-id} attribute and bumps a generation counter, so a later action
 * can resolve the same DOM node by a stable id or a CSS path. When the DOM re-renders
 * and the node is gone, actions fail honestly instead of pretending to succeed.</p>
 */
public final class PageObserver {

    private static final int MAX_ELEMENTS = 220;
    private static final int MAX_LINKS = 60;
    private static final int MAX_TABLES = 12;
    private static final int MAX_TEXT_PREVIEW = 6000;

    private static final String SENSITIVE_TYPES = "password,hidden,email,tel,date,"
            + "datetime-local,month,week,time,file,color,range";

    private static final String SNAPSHOT_SCRIPT =
            "(function(){" +
            "var gen=(window.__kaoriAiSnapshot||0)+1;" +
            "window.__kaoriAiSnapshot=gen;" +
            "function visible(el){if(!el)return false;var r=el.getBoundingClientRect();" +
            "if(r.width<=0&&r.height<=0)return false;" +
            "var s=window.getComputedStyle(el);" +
            "if(!s)return true;" +
            "if(s.visibility==='hidden'||s.display==='none'||s.opacity==='0')return false;" +
            "return true;}" +
            "function clean(v,n){v=(v||'').replace(/\\s+/g,' ').trim();" +
            "if(v.length>n){v=v.slice(0,n)+'…';}return v;}" +
            "function textOf(el){return clean(el.innerText||el.textContent||'',180);}" +
            "function labelOf(el){return clean(el.getAttribute('aria-label')||el.getAttribute('title')||'',120);}" +
            "function pathOf(el){var parts=[],node=el,depth=0;" +
            "while(node&&node.nodeType===1&&depth<4){" +
            "var tag=node.tagName.toLowerCase();" +
            "var parent=node.parentElement;" +
            "if(!parent){parts.unshift(tag);break;}" +
            "var index=1,sib=node;" +
            "while((sib=sib.previousElementSibling)){if(sib.tagName===node.tagName){index++;}}" +
            "parts.unshift(tag+':nth-of-type('+index+')');" +
            "node=parent;depth++;}" +
            "return parts.join(' > ');}" +
            "var selector='a[href],button,input,select,textarea,[role=button],[role=link]," +
            "[role=checkbox],[role=radio],[role=tab],[role=menuitem],[role=switch]," +
            "[role=searchbox],[role=textbox],[contenteditable=true],[onclick],[tabindex]';" +
            "var nodes=document.querySelectorAll(selector);" +
            "var sensitive='" + SENSITIVE_TYPES + "';" +
            "var elements=[],counter=0;" +
            "for(var i=0;i<nodes.length&&counter<" + MAX_ELEMENTS + ";i++){" +
            "var el=nodes[i];var tag=(el.tagName||'').toLowerCase();" +
            "if(tag==='html'||tag==='body')continue;" +
            "if(!visible(el))continue;" +
            "counter++;var id='e'+counter;" +
            "try{el.setAttribute('data-kaori-ai-id',id);}catch(e){}" +
            "var r=el.getBoundingClientRect();var type=(el.getAttribute('type')||'').toLowerCase();" +
            "var isSensitive=sensitive.split(',').indexOf(type)>=0;" +
            "var rawValue=(typeof el.value==='string')?el.value:'';var hasValue=rawValue.length>0;" +
            "elements.push({element_id:id,tag:tag," +
            "role:el.getAttribute('role')||'',type:type," +
            "text:textOf(el),label:labelOf(el)," +
            "name:clean(el.getAttribute('name')||'',80)," +
            "placeholder:clean(el.getAttribute('placeholder')||'',80)," +
            "href:tag==='a'?clean(el.href||'',300):''," +
            "value:isSensitive?'':clean(rawValue,60)," +
            "has_value:hasValue,sensitive:isSensitive," +
            "disabled:!!el.disabled," +
            "in_viewport:(r.top<window.innerHeight&&r.bottom>0)," +
            "selector:pathOf(el)});" +
            "}" +
            "var forms=[];var formNodes=document.querySelectorAll('form');" +
            "for(var f=0;f<formNodes.length&&forms.length<10;f++){" +
            "var fm=formNodes[f];var fields=[];var fieldNodes=fm.querySelectorAll('input,select,textarea');" +
            "for(var k=0;k<fieldNodes.length&&fields.length<25;k++){" +
            "var fe=fieldNodes[k];" +
            "fields.push({tag:(fe.tagName||'').toLowerCase(),type:(fe.getAttribute('type')||'').toLowerCase()," +
            "name:clean(fe.getAttribute('name')||'',60),placeholder:clean(fe.getAttribute('placeholder')||'',60)," +
            "required:!!fe.required});}" +
            "forms.push({index:f,action:clean(fm.action||'',200),method:(fm.method||'get').toLowerCase(),fields:fields});}" +
            "var links=[];var anchors=document.querySelectorAll('a[href]');" +
            "for(var a=0;a<anchors.length&&links.length<" + MAX_LINKS + ";a++){" +
            "var an=anchors[a];var href=an.href||'';" +
            "if(!href||href.indexOf('javascript:')===0)continue;" +
            "links.push({text:clean(an.innerText||an.textContent||'',120)||clean(href,120),href:clean(href,300)});}" +
            "var tables=[];var tableNodes=document.querySelectorAll('table');" +
            "for(var t=0;t<tableNodes.length&&tables.length<" + MAX_TABLES + ";t++){" +
            "var tb=tableNodes[t];var rows=tb.rows?tb.rows.length:0;" +
            "var cols=(rows>0&&tb.rows[0])?tb.rows[0].cells.length:0;" +
            "var cap=(tb.caption&&tb.caption.innerText)||'';" +
            "tables.push({index:t,rows:rows,cols:cols,caption:clean(cap,200)});}" +
            "var h1=document.querySelector('h1');" +
            "var bodyText=document.body?(document.body.innerText||''):'';" +
            "var docEl=document.documentElement;" +
            "return JSON.stringify({generation:gen,url:location.href,title:document.title||''," +
            "readyState:document.readyState,heading:h1?clean(h1.innerText,200):''," +
            "scrollY:window.scrollY||0,scrollHeight:docEl?docEl.scrollHeight:0," +
            "viewportHeight:window.innerHeight||0,viewportWidth:window.innerWidth||0," +
            "textLength:bodyText.length,textPreview:bodyText.slice(0," + MAX_TEXT_PREVIEW + ")," +
            "dialogOpen:!!document.querySelector('dialog[open]')," +
            "iframeCount:document.querySelectorAll('iframe').length," +
            "elementCount:elements.length,elementTotal:nodes.length," +
            "elements:elements,forms:forms,links:links,tables:tables});" +
            "})()";

    /** Must be called from a background thread. */
    public BrowserObservation observe(BrowserHost host, long timeoutMs) {
        if (host == null) {
            return null;
        }
        WebView webView = host.currentWebView();
        if (webView == null) {
            return null;
        }
        String raw = JsBridge.evaluate(host, webView, SNAPSHOT_SCRIPT, timeoutMs);
        if (raw == null) {
            return null;
        }
        try {
            Object parsed = new JSONTokener(raw).nextValue();
            if (!(parsed instanceof String)) {
                return null;
            }
            String json = ((String) parsed).trim();
            if (json.isEmpty() || "null".equals(json)) {
                return null;
            }
            return BrowserObservation.fromJson(new JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    /** Lightweight visibility check used by wait/verification steps. */
    public boolean elementExists(BrowserHost host, String elementId, long timeoutMs) {
        WebView webView = host == null ? null : host.currentWebView();
        if (webView == null || elementId == null || elementId.isEmpty()) {
            return false;
        }
        String script = "(function(){" +
                "var el=document.querySelector('[data-kaori-ai-id=\"" + elementId + "\"]');" +
                "if(!el)return 'false';" +
                "var r=el.getBoundingClientRect();" +
                "if(r.width<=0&&r.height<=0)return 'false';" +
                "return 'true';})()";
        String value = JsBridge.evaluate(host, webView, script, timeoutMs);
        return value != null && value.contains("true");
    }
}
