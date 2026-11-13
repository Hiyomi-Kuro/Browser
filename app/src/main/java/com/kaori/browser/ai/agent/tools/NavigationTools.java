package com.kaori.browser.ai.agent.tools;

import com.kaori.browser.ai.agent.UrlTools;

import org.json.JSONObject;

/** browser.open_url / back / forward / reload. */
public final class NavigationTools {

    private NavigationTools() {
    }

    public static final class OpenUrl implements BrowserTool {
        @Override
        public String name() {
            return "browser.open_url";
        }

        @Override
        public String description() {
            return "Open an http:// or https:// URL in the current tab and wait for it to load.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "url", ToolSchemas.stringProperty("Absolute http(s) URL to open")), "url");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            String url = UrlTools.ensureScheme(arguments.optString("url", ""));
            if (UrlTools.isDangerousScheme(url)) {
                return "This URL scheme is not allowed for the agent.";
            }
            if (!UrlTools.isHttpUrl(url)) {
                return "Only http:// and https:// URLs can be opened.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            final String url = UrlTools.ensureScheme(arguments.optString("url", ""));
            String before = context.currentUrl();
            boolean posted = context.onMain(() -> context.host.openUrl(url, false));
            if (!posted) {
                return ToolResult.fail("The browser did not accept the navigation request.");
            }
            boolean loaded = PageLoadWaiter.awaitNavigation(context.host, before,
                    Math.min(context.toolTimeoutMs, 30000));
            String after = context.currentUrl();
            ToolResult result = ToolResult.ok(
                    (loaded ? "Opened " : "Navigation started but did not settle: ") + after)
                    .withPageChange(before, after);
            return result;
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

    public static final class Back implements BrowserTool {
        @Override
        public String name() {
            return "browser.back";
        }

        @Override
        public String description() {
            return "Go back one entry in the current tab's history.";
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
            if (!context.host.canGoBack()) {
                return ToolResult.fail("This tab has no previous page.");
            }
            String before = context.currentUrl();
            if (!context.onMain(context.host::goBack)) {
                return ToolResult.fail("The browser rejected the back request.");
            }
            PageLoadWaiter.awaitSettle(context.host, Math.min(context.toolTimeoutMs, 20000));
            String after = context.currentUrl();
            return ToolResult.ok("Went back to " + after).withPageChange(before, after);
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

    public static final class Forward implements BrowserTool {
        @Override
        public String name() {
            return "browser.forward";
        }

        @Override
        public String description() {
            return "Go forward one entry in the current tab's history.";
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
            if (!context.host.canGoForward()) {
                return ToolResult.fail("This tab has no forward history.");
            }
            String before = context.currentUrl();
            if (!context.onMain(context.host::goForward)) {
                return ToolResult.fail("The browser rejected the forward request.");
            }
            PageLoadWaiter.awaitSettle(context.host, Math.min(context.toolTimeoutMs, 20000));
            String after = context.currentUrl();
            return ToolResult.ok("Went forward to " + after).withPageChange(before, after);
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

    public static final class Reload implements BrowserTool {
        @Override
        public String name() {
            return "browser.reload";
        }

        @Override
        public String description() {
            return "Reload the current page.";
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
            if (!context.onMain(context.host::reload)) {
                return ToolResult.fail("The browser rejected the reload request.");
            }
            PageLoadWaiter.awaitSettle(context.host, Math.min(context.toolTimeoutMs, 25000));
            return ToolResult.ok("Reloaded " + context.currentUrl())
                    .withPageChange(context.currentUrl(), context.currentUrl());
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
}
