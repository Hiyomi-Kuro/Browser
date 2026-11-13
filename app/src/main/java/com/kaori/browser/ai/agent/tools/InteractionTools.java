package com.kaori.browser.ai.agent.tools;

import com.kaori.browser.ai.agent.observation.BrowserObservation;
import com.kaori.browser.ai.agent.observation.ElementInfo;

import org.json.JSONArray;
import org.json.JSONObject;

/** browser.click / type / clear / scroll / select / wait / submit. */
public final class InteractionTools {

    private InteractionTools() {
    }

    private static ElementInfo elementOf(ToolContext context, String elementId) {
        BrowserObservation observation = context.latestObservation();
        return observation == null ? null : observation.findElement(elementId);
    }

    private static String selectorOf(ToolContext context, String elementId) {
        ElementInfo element = elementOf(context, elementId);
        return element == null ? "" : element.selector;
    }

    public static final class Click implements BrowserTool {
        @Override
        public String name() {
            return "browser.click";
        }

        @Override
        public String description() {
            return "Click a visible, enabled element identified by element_id from the "
                    + "latest snapshot.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "element_id", ToolSchemas.stringProperty("Element id from the latest snapshot")),
                    "element_id");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return ElementIds.validate(arguments.optString("element_id", ""));
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String elementId = arguments.optString("element_id", "");
            String before = context.currentUrl();
            String body =
                    "if(el.disabled){return JSON.stringify({ok:false,reason:'disabled'," +
                    ElementScripts.resultFields() + "});}" +
                    "var clicked=true,err='';" +
                    "try{el.focus();el.click();}catch(e){clicked=false;err=String((e&&e.message)||e);}" +
                    "return JSON.stringify({ok:clicked,reason:err,click_failed:!clicked," +
                    ElementScripts.resultFields() + "});";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(),
                    ElementScripts.action(elementId, selectorOf(context, elementId), body),
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("The click did not complete in time.");
            }
            if (!result.optBoolean("ok", false)) {
                String reason = result.optString("reason", "");
                return ToolResult.fail(result.optBoolean("click_failed", false)
                        ? "The page rejected the click (" + reason + ")."
                        : ToolErrors.reasonText(reason));
            }

            PageLoadWaiter.settleQuietly(700);
            String after = context.currentUrl();
            boolean navigated = !before.equals(after);
            if (navigated) {
                PageLoadWaiter.awaitSettle(context.host,
                        Math.min(context.toolTimeoutMs, 20000));
            }
            String label = result.optString("text", "");
            String message = "Click dispatched on " + elementId
                    + (label.isEmpty() ? "" : " (\"" + label + "\")")
                    + (navigated ? "; the page navigated to " + after
                    : "; the page effect is verified by the next observation");
            return ToolResult.ok(message).withPageChange(before, after);
        }

        @Override
        public Risk risk() {
            return Risk.MEDIUM;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    public static final class Type implements BrowserTool {
        @Override
        public String name() {
            return "browser.type";
        }

        @Override
        public String description() {
            return "Type text into an input, textarea or contenteditable element. Replaces "
                    + "the current value.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "element_id", ToolSchemas.stringProperty("Element id from the latest snapshot"),
                    "text", ToolSchemas.stringProperty("Text to enter (max 2000 characters)")),
                    "element_id", "text");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            String idError = ElementIds.validate(arguments.optString("element_id", ""));
            if (idError != null) {
                return idError;
            }
            String text = arguments.optString("text", "");
            if (text.isEmpty()) {
                return "The text argument must not be empty; use browser.clear instead.";
            }
            if (text.length() > 2000) {
                return "The text argument is limited to 2000 characters.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String elementId = arguments.optString("element_id", "");
            String text = arguments.optString("text", "");
            String before = context.currentUrl();
            String body =
                    "var value=" + JsBridge.quote(text) + ";" +
                    "var tagNow=(el.tagName||'').toLowerCase();" +
                    "if(tagNow==='select'){return JSON.stringify({ok:false," +
                    "reason:'target_is_a_select_use_browser_select'});}" +
                    "if(tagNow==='input'||tagNow==='textarea'){" +
                    "el.focus();" +
                    "var proto=(tagNow==='textarea')?window.HTMLTextAreaElement.prototype:" +
                    "window.HTMLInputElement.prototype;" +
                    "var desc=Object.getOwnPropertyDescriptor(proto,'value');" +
                    "try{if(desc&&desc.set){desc.set.call(el,value);}else{el.value=value;}}" +
                    "catch(e){el.value=value;}" +
                    "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                    "return JSON.stringify({ok:true,length:(el.value||'').length," +
                    "value_readable:!el.type||el.type!=='password'," +
                    ElementScripts.resultFields() + "});}" +
                    "if(el.isContentEditable){" +
                    "el.focus();el.textContent=value;" +
                    "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "return JSON.stringify({ok:true,length:(el.textContent||'').length," +
                    ElementScripts.resultFields() + "});}" +
                    "return JSON.stringify({ok:false,reason:'not_an_input'});";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(),
                    ElementScripts.action(elementId, selectorOf(context, elementId), body),
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("The text entry did not complete in time.");
            }
            if (!result.optBoolean("ok", false)) {
                return ToolResult.fail(ToolErrors.reasonText(result.optString("reason", "")));
            }
            int expected = text.length();
            int actual = result.optInt("length", -1);
            if (actual != expected) {
                return ToolResult.fail("The field reports " + actual
                        + " characters instead of " + expected
                        + "; the site may block scripted input.");
            }
            return ToolResult.ok("Typed " + expected + " characters into " + elementId
                    + " (verified)", null).withPageChange(before, context.currentUrl());
        }

        @Override
        public Risk risk() {
            return Risk.MEDIUM;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    public static final class Clear implements BrowserTool {
        @Override
        public String name() {
            return "browser.clear";
        }

        @Override
        public String description() {
            return "Clear the value of an input, textarea or contenteditable element.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "element_id", ToolSchemas.stringProperty("Element id from the latest snapshot")),
                    "element_id");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return ElementIds.validate(arguments.optString("element_id", ""));
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String elementId = arguments.optString("element_id", "");
            String body =
                    "var tagNow=(el.tagName||'').toLowerCase();" +
                    "if(tagNow==='input'||tagNow==='textarea'){" +
                    "el.focus();" +
                    "var proto=(tagNow==='textarea')?window.HTMLTextAreaElement.prototype:" +
                    "window.HTMLInputElement.prototype;" +
                    "var desc=Object.getOwnPropertyDescriptor(proto,'value');" +
                    "try{if(desc&&desc.set){desc.set.call(el,'');}else{el.value='';}}" +
                    "catch(e){el.value='';}" +
                    "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                    "return JSON.stringify({ok:true,length:(el.value||'').length," +
                    ElementScripts.resultFields() + "});}" +
                    "if(el.isContentEditable){el.focus();el.textContent='';" +
                    "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "return JSON.stringify({ok:true,length:0," + ElementScripts.resultFields() + "});}" +
                    "return JSON.stringify({ok:false,reason:'not_an_input'});";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(),
                    ElementScripts.action(elementId, selectorOf(context, elementId), body),
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("Clearing the field did not complete in time.");
            }
            if (!result.optBoolean("ok", false)) {
                return ToolResult.fail(ToolErrors.reasonText(result.optString("reason", "")));
            }
            int length = result.optInt("length", -1);
            if (length != 0) {
                return ToolResult.fail("The field still holds " + length + " characters.");
            }
            return ToolResult.ok("Cleared " + elementId + " (verified)");
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    public static final class Scroll implements BrowserTool {
        @Override
        public String name() {
            return "browser.scroll";
        }

        @Override
        public String description() {
            return "Scroll the page up, down, to the top or to the bottom.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "direction", ToolSchemas.enumProperty("Scroll direction",
                            "up", "down", "top", "bottom"),
                    "amount", ToolSchemas.integerProperty("Number of viewports to scroll", 1, 10)),
                    "direction");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            String direction = arguments.optString("direction", "");
            if (!"up".equals(direction) && !"down".equals(direction)
                    && !"top".equals(direction) && !"bottom".equals(direction)) {
                return "direction must be one of: up, down, top, bottom.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String direction = arguments.optString("direction", "down");
            int amount = Math.max(1, Math.min(10, arguments.optInt("amount", 1)));
            String body =
                    "var dir=" + JsBridge.quote(direction) + ";" +
                    "var amount=" + amount + ";" +
                    "var before=window.scrollY||0;" +
                    "if(dir==='top'){window.scrollTo(0,0);}" +
                    "else if(dir==='bottom'){window.scrollTo(0," +
                    "(document.documentElement?document.documentElement.scrollHeight:0));}" +
                    "else{var dy=amount*(window.innerHeight||600)*0.85;" +
                    "window.scrollBy(0,dir==='up'?-dy:dy);}" +
                    "return JSON.stringify({ok:true,before:before,after:window.scrollY||0," +
                    "height:(document.documentElement?document.documentElement.scrollHeight:0)," +
                    "viewport:window.innerHeight||0});";
            String script = "(function(){" + body + "})()";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(), script,
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("The scroll did not complete in time.");
            }
            PageLoadWaiter.settleQuietly(300);
            int before = result.optInt("before", 0);
            int after = result.optInt("after", 0);
            if (before == after && !"top".equals(direction) && !"bottom".equals(direction)) {
                return ToolResult.fail("The page did not scroll; it may already be at the edge.");
            }
            return ToolResult.ok("Scrolled " + direction + " from " + before + " to " + after);
        }

        @Override
        public Risk risk() {
            return Risk.LOW;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    public static final class Select implements BrowserTool {
        @Override
        public String name() {
            return "browser.select";
        }

        @Override
        public String description() {
            return "Choose an option in a <select> dropdown by value or visible text.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "element_id", ToolSchemas.stringProperty("Element id from the latest snapshot"),
                    "value", ToolSchemas.stringProperty("Option value or visible text")),
                    "element_id", "value");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            String idError = ElementIds.validate(arguments.optString("element_id", ""));
            if (idError != null) {
                return idError;
            }
            if (arguments.optString("value", "").trim().isEmpty()) {
                return "The value argument must not be empty.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String elementId = arguments.optString("element_id", "");
            String value = arguments.optString("value", "");
            String body =
                    "if((el.tagName||'').toLowerCase()!=='select'){" +
                    "return JSON.stringify({ok:false,reason:'not_select'});}" +
                    "var wanted=" + JsBridge.quote(value) + ";" +
                    "var options=[];var matched=-1;" +
                    "for(var i=0;i<el.options.length;i++){" +
                    "var o=el.options[i];var text=(o.text||'').trim();" +
                    "if(options.length<30)options.push(o.value+' | '+text);" +
                    "if(matched<0&&(o.value===wanted||text===wanted)){matched=i;}}" +
                    "if(matched<0){return JSON.stringify({ok:false,reason:'option_not_found'," +
                    "options:options});}" +
                    "el.selectedIndex=matched;" +
                    "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                    "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                    "return JSON.stringify({ok:true,value:el.value,index:matched," +
                    ElementScripts.resultFields() + "});";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(),
                    ElementScripts.action(elementId, selectorOf(context, elementId), body),
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("Selecting the option did not complete in time.");
            }
            if (!result.optBoolean("ok", false)) {
                String reason = result.optString("reason", "");
                if ("option_not_found".equals(reason)) {
                    StringBuilder builder = new StringBuilder(
                            "The dropdown has no option matching \"" + value + "\". Available:");
                    JSONArray options = result.optJSONArray("options");
                    if (options != null) {
                        for (int i = 0; i < options.length(); i++) {
                            builder.append("\n- ").append(options.optString(i, ""));
                        }
                    }
                    return ToolResult.fail(builder.toString());
                }
                return ToolResult.fail(ToolErrors.reasonText(reason));
            }
            PageLoadWaiter.settleQuietly(300);
            return ToolResult.ok("Selected \"" + result.optString("value", value)
                    + "\" in " + elementId + " (verified)");
        }

        @Override
        public Risk risk() {
            return Risk.MEDIUM;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }

    public static final class Wait implements BrowserTool {
        @Override
        public String name() {
            return "browser.wait";
        }

        @Override
        public String description() {
            return "Wait for a fixed delay, for an element to appear, or for text to appear.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "ms", ToolSchemas.integerProperty("Delay in milliseconds", 0, 15000),
                    "element_id", ToolSchemas.stringProperty("Wait until this element is visible"),
                    "text", ToolSchemas.stringProperty("Wait until this text appears on the page")));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            boolean hasMs = arguments.has("ms");
            boolean hasElement = !arguments.optString("element_id", "").isEmpty();
            boolean hasText = !arguments.optString("text", "").isEmpty();
            if (!hasMs && !hasElement && !hasText) {
                return "Provide ms, element_id or text to wait for.";
            }
            if (hasElement) {
                String idError = ElementIds.validate(arguments.optString("element_id", ""));
                if (idError != null) {
                    return idError;
                }
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            int ms = Math.max(0, Math.min(15000, arguments.optInt("ms", 0)));
            String elementId = arguments.optString("element_id", "");
            String text = arguments.optString("text", "");
            long timeout = Math.max(3000, Math.min(context.toolTimeoutMs, 15000));

            if (!elementId.isEmpty()) {
                long deadline = System.currentTimeMillis() + timeout;
                while (System.currentTimeMillis() < deadline) {
                    if (context.observer.elementExists(context.host, elementId, 3000)) {
                        return ToolResult.ok("Element " + elementId + " is visible.");
                    }
                    PageLoadWaiter.settleQuietly(300);
                }
                return ToolResult.fail("Element " + elementId
                        + " did not become visible within " + timeout + " ms.");
            }

            if (!text.isEmpty()) {
                String script = "(function(){var needle=" + JsBridge.quote(text) + ";" +
                        "var body=document.body?(document.body.innerText||''):'';" +
                        "return JSON.stringify({found:body.toLowerCase().indexOf(" +
                        "needle.toLowerCase())>=0});})()";
                long deadline = System.currentTimeMillis() + timeout;
                while (System.currentTimeMillis() < deadline) {
                    JSONObject result = JsBridge.evaluateJson(context.host, context.webView(),
                            script, 4000);
                    if (result != null && result.optBoolean("found", false)) {
                        return ToolResult.ok("Text found on the page.");
                    }
                    PageLoadWaiter.settleQuietly(400);
                }
                return ToolResult.fail("Text did not appear within " + timeout + " ms.");
            }

            PageLoadWaiter.settleQuietly(ms);
            return ToolResult.ok("Waited " + ms + " ms.");
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

    public static final class Submit implements BrowserTool {
        @Override
        public String name() {
            return "browser.submit";
        }

        @Override
        public String description() {
            return "Submit the form that contains the given element. This sends data to the "
                    + "site and always asks the user first.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "element_id", ToolSchemas.stringProperty(
                            "Element id inside the form to submit (optional)")),
                    "element_id");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            return ElementIds.validate(arguments.optString("element_id", ""));
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            String elementId = arguments.optString("element_id", "");
            String before = context.currentUrl();
            String body =
                    "var form=(el.tagName||'').toLowerCase()==='form'?el:(el.form||" +
                    "(el.closest?el.closest('form'):null));" +
                    "if(!form){return JSON.stringify({ok:false,reason:'no_form'});}" +
                    "var action=form.action||'';" +
                    "try{if(form.requestSubmit){form.requestSubmit();}else{form.submit();}}" +
                    "catch(e){return JSON.stringify({ok:false,reason:String((e&&e.message)||e)});}" +
                    "return JSON.stringify({ok:true,action:action," +
                    ElementScripts.resultFields() + "});";
            JSONObject result = JsBridge.evaluateJson(context.host, context.webView(),
                    ElementScripts.action(elementId, selectorOf(context, elementId), body),
                    context.toolTimeoutMs);
            if (result == null) {
                return ToolResult.fail("Submitting the form did not complete in time.");
            }
            if (!result.optBoolean("ok", false)) {
                return ToolResult.fail(ToolErrors.reasonText(result.optString("reason", "")));
            }
            PageLoadWaiter.awaitNavigation(context.host, before,
                    Math.min(context.toolTimeoutMs, 25000));
            String after = context.currentUrl();
            return ToolResult.ok("Form submitted to " + result.optString("action", "(no action)")
                    + (before.equals(after) ? "; the page did not navigate" : "; now at " + after))
                    .withPageChange(before, after);
        }

        @Override
        public Risk risk() {
            return Risk.HIGH;
        }

        @Override
        public boolean readOnly() {
            return false;
        }
    }
}
