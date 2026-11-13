package com.kaori.browser.ai.agent.tools;

import com.kaori.browser.ai.agent.TabInfo;
import com.kaori.browser.ai.agent.UrlTools;

import org.json.JSONObject;

import java.util.List;

/** browser.new_tab / switch_tab / list_tabs / close_tab. */
public final class TabTools {

    private TabTools() {
    }

    public static final class NewTab implements BrowserTool {
        @Override
        public String name() {
            return "browser.new_tab";
        }

        @Override
        public String description() {
            return "Open a new tab and load the URL in it. The agent owns the new tab and may "
                    + "close it later.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "url", ToolSchemas.stringProperty("Absolute http(s) URL to open")));
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            String url = UrlTools.ensureScheme(arguments.optString("url", ""));
            if (!UrlTools.isHttpUrl(url)) {
                return "Only http:// and https:// URLs can be opened.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            final String url = UrlTools.ensureScheme(arguments.optString("url", ""));
            String before = context.currentUrl();
            if (!context.onMain(() -> context.host.openUrl(url, true))) {
                return ToolResult.fail("The browser did not create a new tab.");
            }
            PageLoadWaiter.awaitSettle(context.host, Math.min(context.toolTimeoutMs, 25000));
            int index = context.host.currentTabIndex();
            context.session.addOwnedTab(index);
            String after = context.currentUrl();
            return ToolResult.ok("Opened tab " + index + " at " + after)
                    .withPageChange(before, after);
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

    public static final class SwitchTab implements BrowserTool {
        @Override
        public String name() {
            return "browser.switch_tab";
        }

        @Override
        public String description() {
            return "Switch to the tab at the given index from browser.list_tabs.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "index", ToolSchemas.integerProperty("Tab index", 0, 64)), "index");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            if (arguments.optInt("index", -1) < 0) {
                return "index must be 0 or greater.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            final int index = arguments.optInt("index", 0);
            String before = context.currentUrl();
            if (!context.onMain(() -> context.host.switchToTab(index))) {
                return ToolResult.fail("There is no tab with index " + index + ".");
            }
            PageLoadWaiter.awaitSettle(context.host, Math.min(context.toolTimeoutMs, 15000));
            String after = context.currentUrl();
            return ToolResult.ok("Switched to tab " + index + (after.isEmpty() ? ""
                    : " (" + after + ")")).withPageChange(before, after);
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

    public static final class ListTabs implements BrowserTool {
        @Override
        public String name() {
            return "browser.list_tabs";
        }

        @Override
        public String description() {
            return "List every open tab with its index, title, URL and owner.";
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
            List<TabInfo> tabs = context.host.listTabs();
            if (tabs == null || tabs.isEmpty()) {
                return ToolResult.fail("No tabs are open.");
            }
            StringBuilder builder = new StringBuilder();
            builder.append(tabs.size()).append(" tab(s); the active tab is marked with *.\n");
            for (TabInfo tab : tabs) {
                builder.append(tab.describe()).append('\n');
            }
            return ToolResult.ok(tabs.size() + " tabs", builder.toString());
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

    public static final class CloseTab implements BrowserTool {
        @Override
        public String name() {
            return "browser.close_tab";
        }

        @Override
        public String description() {
            return "Close a tab created by the agent. Closing a user tab requires the user's "
                    + "confirmation.";
        }

        @Override
        public JSONObject parameters() {
            return ToolSchemas.schema(ToolSchemas.properties(
                    "index", ToolSchemas.integerProperty("Tab index", 0, 64)), "index");
        }

        @Override
        public String validateArguments(JSONObject arguments) {
            if (arguments.optInt("index", -1) < 0) {
                return "index must be 0 or greater.";
            }
            return null;
        }

        @Override
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            final int index = arguments.optInt("index", 0);
            String before = context.currentUrl();
            final boolean[] closed = new boolean[]{false};
            context.onMain(() -> closed[0] = context.host.closeAgentTab(index));
            if (!closed[0]) {
                return ToolResult.fail("Tab " + index
                        + " was not closed. Only tabs created by the agent can be closed "
                        + "without confirmation, and the last tab must stay open.");
            }
            PageLoadWaiter.awaitSettle(context.host, 8000);
            return ToolResult.ok("Closed tab " + index)
                    .withPageChange(before, context.currentUrl());
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
