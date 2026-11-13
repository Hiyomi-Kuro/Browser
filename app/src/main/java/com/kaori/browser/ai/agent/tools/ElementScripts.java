package com.kaori.browser.ai.agent.tools;

/**
 * Builds element-scoped JavaScript.
 *
 * <p>The element id, the CSS path and any text are JSON-quoted before interpolation, so
 * a model can never inject JavaScript. Only validated ids reach this class.</p>
 */
public final class ElementScripts {

    private ElementScripts() {
    }

    /** Wraps {@code body} in an IIFE that resolves the element into {@code el}. */
    public static String action(String elementId, String selector, String body) {
        return "(function(){" +
                "var id=" + JsBridge.quote(elementId) + ";" +
                "var sel=" + JsBridge.quote(selector) + ";" +
                "var gen=(window.__kaoriAiSnapshot||0);" +
                "var el=document.querySelector('[data-kaori-ai-id=\"'+id+'\"]');" +
                "var by='id';" +
                "if(!el&&sel){try{el=document.querySelector(sel);}catch(e){el=null;}" +
                "if(el){by='selector';}}" +
                "if(!el){return JSON.stringify({ok:false,reason:'element_not_found'," +
                "generation:gen,expected_generation:gen});}" +
                "if(!el.isConnected){return JSON.stringify({ok:false,reason:'element_detached'});}" +
                "try{el.scrollIntoView({block:'center',inline:'center'});}catch(e){}" +
                "var rect=el.getBoundingClientRect();" +
                "if(rect.width<=0&&rect.height<=0){return JSON.stringify({ok:false,reason:'not_visible'});}" +
                "var tag=(el.tagName||'').toLowerCase();" +
                "var role=el.getAttribute('role')||'';" +
                "var text=((el.innerText||el.textContent||'')+'').replace(/\\s+/g,' ').trim().slice(0,120);" +
                body +
                "})()";
    }

    /** Shared result fields appended by each action body. */
    static String resultFields() {
        return "tag:tag,role:role,text:text,resolved_by:by,generation:gen";
    }
}
